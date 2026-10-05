package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.Bank.FormHelper;
import com.example.demo.Bank.HfbConst;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.enums.BankNotifyStatusEnum;
import com.example.demo.enums.BindStatusEnum;
import com.example.demo.enums.ContractBizTypeEnum;
import com.example.demo.enums.ContractTypeEnum;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.enums.UserTypeEnum;
import com.example.demo.mapper.LendItemMapper;
import com.example.demo.service.LendItemService;
import com.example.demo.service.BankAccountSyncService;
import com.example.demo.service.BankDatabaseSyncService;
import com.example.demo.service.LendService;
import com.example.demo.service.RecommendService;
import com.example.demo.service.TransFlowService;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.example.demo.utils.BalanceGuard;
import com.example.demo.utils.LendNoUtils;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.wwfinance.common.utils.BankSignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.demo.utils.PageUtils;

@Slf4j
@Service
public class LendItemServiceImpl extends ServiceImpl<LendItemMapper, LendItem> implements LendItemService {

    /** 金额格式：正数，最多两位小数 */
    private static final String AMOUNT_REGEX = "\\d+(\\.\\d{1,2})?";

    @Autowired
    private LendService lendService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private BankAccountSyncService bankAccountSyncService;

    @Autowired
    private BankDatabaseSyncService bankDatabaseSyncService;

    @Autowired
    private TransFlowService transFlowService;

    @Autowired
    private UserBindService userBindService;

    @Autowired
    private com.example.demo.service.UserIntegralService userIntegralService;

    @Autowired
    private com.example.demo.service.MessageService messageService;

    @Autowired
    private com.example.demo.service.ContractService contractService;

    @Autowired
    private RecommendService recommendService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 标的剩余可投额度 Redis key 前缀 */
    private static final String LEFT_AMOUNT_KEY = "lend:leftAmount:";

    @Override
    public String buildInvestForm(Long lendId, Long userId, BigDecimal amount) {
        // 1. 校验投资人
        User user = userService.getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(500, "用户不存在");
        }
        if (!UserTypeEnum.INVESTOR.getCode().equals(user.getUserType())) {
            throw new ResourceNotFoundException(500, "你不是投资人，请去认证投资人");
        }

        // 2. 校验绑卡（本地 + 银行侧都须存在，否则银行页面会因查无此记录而渲染异常）
        UserBind userBind = userBindService.getByUserId(userId);
        if (userBind == null || !BindStatusEnum.BOUND.getCode().equals(userBind.getStatus())) {
            throw new ResourceNotFoundException(500, "请先完成绑卡");
        }
        if (!bankDatabaseSyncService.checkBindCodeExists(userBind.getBindCode())) {
            log.warn("绑卡记录未同步到银行库，userId={}, bindCode={}", userId, userBind.getBindCode());
            throw new ResourceNotFoundException(500, "绑卡记录未同步到银行，请解绑后重新绑卡");
        }
        userBind.setMobile(user.getMobile());

        // 3. 校验标的
        Lend lend = lendService.getById(lendId);
        if (lend == null) {
            throw new ResourceNotFoundException(500, "标的不存在");
        }
        if (!LendStatusEnum.FUNDING.getCode().equals(lend.getStatus())) {
            throw new ResourceNotFoundException(500, "标的不在募资中，无法投标");
        }
        if (lend.getUserId().equals(userId)) {
            throw new ResourceNotFoundException(500, "不能投资自己的标的");
        }

        // 4. 校验投资金额
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResourceNotFoundException(500, "投资金额必须大于0");
        }
        // 精度必须在这里拦住：回调侧 handleInvestNotify 用 AMOUNT_REGEX（\\d+(\\.\\d{1,2})?）
        // 校验银行回传金额，三位小数会被判非法并 return false —— 此时用户的钱在银行侧
        // 已经冻结，平台却不入账、也不生成投资记录，形成"钱挂账"。前端输入框不是安全边界。
        if (amount.scale() > 2) {
            throw new ResourceNotFoundException(500, "投资金额最多保留两位小数");
        }
        BigDecimal lowestAmount = lend.getLowestAmount() != null ? lend.getLowestAmount() : new BigDecimal("100");
        if (amount.compareTo(lowestAmount) < 0) {
            throw new ResourceNotFoundException(500, "投资金额不能低于最低起投金额：" + lowestAmount);
        }

        // 4.5 签署关口：《出借协议》未签不得下单。
        //     前端拿到该提示后会调用 /api/core/contract/auth/prepare 生成合同并引导签名，
        //     签完按相同金额重新提交即可放行（金额不同会被要求重新签署）。
        contractService.assertSigned(ContractBizTypeEnum.LEND, lendId, ContractTypeEnum.INVEST, userId, amount);

        // 5. 校验剩余可投额度
        //
        // 注意：这里只是"下单前的友好提示"，**不是**防超募的真正闸门。
        // 真正生效的原子闸门在银行回调里：baseMapper.atomicInvest 走条件 UPDATE
        // （SET invest_amount = invest_amount + ? WHERE amount - invest_amount >= ?），
        // 由数据库行锁保证同一标的不会投超。本路径（buildInvestForm）不写任何库，
        // 因此也不需要任何互斥。
        //
        // 历史实现的两处缺陷，本次一并修正：
        //  ① 原先这里加了一把 Redis 锁 "lend:leftAmount:<lendId>:lock"，但锁内只做了一次
        //     "重新查银行余额"的只读操作 —— 既保护不了任何写入，又因为 key 是按【标的】加锁，
        //     会让同一标的上并发投标的【不同用户】互相顶掉、误报"请勿重复提交"
        //     （热门标的必现）；且 unlock 不校验持有者，A 能直接删掉 B 持有的锁。故整体移除。
        //  ② 原先 "if (lend.getUserId() != null)" 恒为 true（借款人不可能为空），
        //     导致上面的 Redis 缓存判断必然被下一段的 DB 值覆盖，缓存形同虚设。
        //     改为两条独立判断，命中哪条报哪条。
        String leftKey = LEFT_AMOUNT_KEY + lendId;
        Object cached = redisTemplate.opsForValue().get(leftKey);
        if (cached != null) {
            BigDecimal cachedLeft = new BigDecimal(cached.toString());
            if (cachedLeft.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResourceNotFoundException(500, "标的已满标");
            }
            if (cachedLeft.compareTo(amount) < 0) {
                throw new ResourceNotFoundException(500, "剩余可投金额不足，剩余：" + cachedLeft);
            }
        }
        BigDecimal totalAmount = lend.getAmount() != null ? lend.getAmount() : BigDecimal.ZERO;
        BigDecimal investedAmount = lend.getInvestAmount() != null ? lend.getInvestAmount() : BigDecimal.ZERO;
        if (totalAmount.subtract(investedAmount).compareTo(amount) < 0) {
            throw new ResourceNotFoundException(500, "剩余可投金额不足");
        }

        // 6. 校验投资人余额（从银行查询，平台不持有余额）
        //    这里的 actionName 传空串：LendController 已经统一加了"投标失败："前缀，
        //    再传一遍会得到"投标失败：投标失败：可用余额不足…"
        BigDecimal balance = bankAccountSyncService.getBankBalance(userId);
        BalanceGuard.require("", balance, amount);

        // 7. 冻结由银行执行，平台不操作真实资金：
        //    银行回调时冻结投资人资金，平台仅记录投标记录（见 handleInvestNotify）。

        // 8. 组装旺旺银行投标参数并签名
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", HfbConst.AGENT_ID);
        paramMap.put("agentUserId", userBind.getMobile());
        paramMap.put("voteBindCode", userBind.getBindCode());
        paramMap.put("agentBillNo", LendNoUtils.getLendItemNo());
        paramMap.put("voteAmt", amount);
        paramMap.put("votePrizeAmt", "0");
        paramMap.put("voteFeeAmt", "0");
        paramMap.put("agentProjectCode", lendId);
        paramMap.put("agentProjectName", lend.getTitle());
        paramMap.put("projectAmt", lend.getAmount().toString());
        paramMap.put("returnUrl", HfbConst.INVEST_RETURN_URL);
        paramMap.put("notifyUrl", HfbConst.INVEST_NOTIFY_URL);
        paramMap.put("timestamp", new Date().getTime());
        paramMap.put("sign", BankSignUtil.getSign(paramMap));

        log.info("投标表单生成，userId={}, lendId={}, amount={}", userId, lendId, amount);
        return FormHelper.buildForm(HfbConst.INVEST_URL, paramMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleInvestNotify(Map<String, Object> params) {
        // 1. 验签
        if (!BankSignUtil.isSignEquals(params)) {
            log.warn("投标回调验签失败，params={}", params);
            return false;
        }

        String agentUserId = (String) params.get("agentUserId");
        String amountStr = (String) params.getOrDefault("amount", params.get("voteAmt"));
        String transNo = (String) params.getOrDefault("transNo", params.get("agentBillNo"));
        String status = (String) params.getOrDefault("status", params.get("resultCode"));
        String projectIdStr = (String) params.getOrDefault("projectId", params.get("agentProjectCode"));

        if ("0001".equals(status)) {
            status = BankNotifyStatusEnum.SUCCESS.getCode();
        }

        // 2. 银行侧处理失败：无需重试，应答 success
        if (!BankNotifyStatusEnum.isSuccess(status)) {
            log.info("投标回调银行状态非成功，status={}", status);
            return true;
        }

        // 3. 金额格式校验
        if (amountStr == null || !amountStr.matches(AMOUNT_REGEX)) {
            log.warn("投标回调金额非法，amount={}", amountStr);
            return false;
        }

        // 4. 查询投资人（银行回调只回传 bindCode，不传 agentUserId）
        User user = resolveNotifyUser(params);
        if (user == null) {
            log.warn("投标回调用户不存在，agentUserId={}, bindCode={}",
                    agentUserId, params.get("bindCode"));
            return false;
        }

        // 5. 幂等：同一银行流水号不重复入账
        if (transNo != null && transFlowService.existsByTransNo(transNo)) {
            log.info("投标回调重复通知，直接确认，transNo={}", transNo);
            return true;
        }

        // 6. 解析标的ID
        Long lendId;
        try {
            lendId = Long.valueOf(projectIdStr);
        } catch (NumberFormatException e) {
            log.warn("投标回调projectId非法，projectId={}", projectIdStr);
            return false;
        }

        BigDecimal amount = new BigDecimal(amountStr);
        LocalDateTime now = LocalDateTime.now();

        // 7. 二次校验标的（回调时状态可能已变）
        Lend lend = lendService.getById(lendId);
        if (lend == null) {
            log.warn("投标回调标的不存在，lendId={}", lendId);
            return false;
        }
        if (!LendStatusEnum.FUNDING.getCode().equals(lend.getStatus())) {
            log.warn("投标回调标的不在募资中，lendId={}, status={}", lendId, lend.getStatus());
            return true;
        }

        // 8. 原子更新标的已募集金额（防超募）
        int rows = baseMapper.atomicInvest(lendId, amount);
        if (rows == 0) {
            log.warn("投标回调原子更新失败（标的已满或剩余可投不足），lendId={}, amount={}", lendId, amount);
            return true;
        }

        // 9. 投标资金冻结由银行处理（平台不碰资金，仅记录流水和投资记录）

        // 10. 记录投标流水（使用银行流水号做幂等）
        recordInvestFlow(user, amount, lend, now, transNo);

        // 11. 生成投资记录
        LendItem lendItem = new LendItem();
        lendItem.setLendItemNo(LendNoUtils.getLendItemNo());
        lendItem.setLendId(lendId);
        lendItem.setInvestUserId(user.getId());
        lendItem.setInvestName(user.getName() != null ? user.getName() : user.getMobile());
        lendItem.setInvestAmount(amount);
        lendItem.setLendYearRate(lend.getLendYearRate());
        lendItem.setInvestTime(now);
        BigDecimal expectAmount = calcExpectAmount(amount, lend.getLendYearRate(), lend.getPeriod());
        lendItem.setExpectAmount(expectAmount);
        lendItem.setStatus(0);
        lendItem.setCreateTime(now);
        lendItem.setUpdateTime(now);
        this.save(lendItem);

        // 投资成功通知（给投资人）
        messageService.sendInvestNotice(user.getId(), amount, lend.getTitle());

        // 12. 检查是否满标
        Lend updatedLend = lendService.getById(lendId);
        if (updatedLend.getInvestAmount().compareTo(updatedLend.getAmount()) >= 0) {
            updatedLend.setStatus(LendStatusEnum.COMPLETED.getCode());
            updatedLend.setUpdateTime(now);
            lendService.updateById(updatedLend);
            // 满标后同步把该标的全部投资记录置为满标状态（否则撤标校验的死分支永远进不去）
            this.update(new LambdaUpdateWrapper<LendItem>()
                    .eq(LendItem::getLendId, lendId)
                    .set(LendItem::getStatus, 2)
                    .set(LendItem::getUpdateTime, now));
            // 满标后缓存置 0，后续投标请求在入口前置拦截被直接拒绝
            redisTemplate.opsForValue().set(LEFT_AMOUNT_KEY + lendId, "0");
            // 该标的退出募资池 → 所有用户的推荐结果都要变
            recommendService.evictAll();
            log.info("标的 {} 已满标，状态变更为待放款", lendId);
            // 资金安全：投资人资金已冻结，此时若借款人不签《放款确认书》，
            // 放款会被卡住且无任何退资出口 → 资金无限期悬停。主动催签，把"可发现"前置。
            messageService.sendMakeLoanPendingNotice(
                    updatedLend.getUserId(), updatedLend.getLendNo(), updatedLend.getAmount());
        } else {
            // 未满标，缓存剩余可投额度，供投标入口前置拦截使用
            BigDecimal leftAmount = updatedLend.getAmount().subtract(updatedLend.getInvestAmount());
            redisTemplate.opsForValue().set(LEFT_AMOUNT_KEY + lendId, leftAmount.toString());
            // 该用户已投过此标 → 只失效他本人的推荐缓存（其他用户不受影响）
            recommendService.evict(user.getId());
        }

        log.info("投标回调处理成功，userId={}, lendId={}, amount={}, transNo={}",
                user.getId(), lendId, amount, transNo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelInvest(Long lendItemId, Long userId) {
        LendItem lendItem = this.getById(lendItemId);
        if (lendItem == null) {
            throw new ResourceNotFoundException(500, "投资记录不存在");
        }
        if (!lendItem.getInvestUserId().equals(userId)) {
            throw new ResourceNotFoundException(500, "只能撤销自己的投资");
        }
        if (lendItem.getStatus() != null && lendItem.getStatus() == 2) {
            throw new ResourceNotFoundException(500, "标的已满标，无法撤销");
        }
        if (lendItem.getStatus() != null && lendItem.getStatus() == 1) {
            throw new ResourceNotFoundException(500, "已撤标");
        }

        // 校验标的状态：放款后（非募资中）不可撤标，否则账目对不上
        Lend lend = lendService.getById(lendItem.getLendId());
        if (lend == null) {
            throw new ResourceNotFoundException(500, "标的不存在");
        }
        if (!LendStatusEnum.FUNDING.getCode().equals(lend.getStatus())) {
            throw new ResourceNotFoundException(500, "标的已放款，无法撤销投资");
        }

        // 撤回：标记状态为已撤标，银行自动解冻冻结资金
        lendItem.setStatus(1);
        lendItem.setUpdateTime(LocalDateTime.now());
        this.updateById(lendItem);

        // 更新标的已投金额和剩余缓存
        BigDecimal amount = lendItem.getInvestAmount();
        lend.setInvestAmount(lend.getInvestAmount().subtract(amount));
        lend.setUpdateTime(LocalDateTime.now());
        lendService.updateById(lend);
        // 更新 Redis 缓存剩余额度
        String leftKey = LEFT_AMOUNT_KEY + lendItem.getLendId();
        BigDecimal leftAmount = lend.getAmount().subtract(lend.getInvestAmount());
        redisTemplate.opsForValue().set(leftKey, leftAmount.toString());

        // 撤标后剩余额度增多，该标的对所有人的推荐价值变化 → 统一失效
        recommendService.evictAll();

        log.info("投资撤回成功，lendItemId={}, userId={}, amount={}。银行将自动解冻冻结资金",
                lendItemId, userId, amount);

        // 撤标通知（给投资人）：告诉他钱已解冻，否则用户只能靠刷新余额自己猜
        messageService.sendInvestCancelNotice(userId, amount, lend.getTitle());
        return true;
    }

    @Override
    public List<LendItem> listByLendId(Long lendId) {
        return this.list(new LambdaQueryWrapper<LendItem>()
                .eq(LendItem::getLendId, lendId)
                .orderByDesc(LendItem::getInvestTime));
    }

    @Override
    public Page<LendItem> pageList(Integer pageNum, Integer pageSize, Long lendId, Long investUserId) {
        Page<LendItem> pageParam = PageUtils.of(pageNum, pageSize);
        LambdaQueryWrapper<LendItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(lendId != null, LendItem::getLendId, lendId);
        wrapper.eq(investUserId != null, LendItem::getInvestUserId, investUserId);
        wrapper.orderByDesc(LendItem::getCreateTime);
        return this.page(pageParam, wrapper);
    }

    @Override
    public com.example.demo.entity.User getUserForInvest(Long userId) {
        return userService.getById(userId);
    }

    @Override
    public com.example.demo.entity.UserBind getBindForInvest(Long userId) {
        return userBindService.getByUserId(userId);
    }

    @Override
    public BigDecimal getBalanceForInvest(Long userId) {
        return bankAccountSyncService.getBankBalance(userId);
    }

    /**
     * 从银行回调参数中解析用户。
     * 投标回调只回传 bindCode，不传 agentUserId；按 bindCode 反查绑卡记录再取用户。
     */
    private User resolveNotifyUser(Map<String, Object> params) {
        String agentUserId = (String) params.get("agentUserId");
        if (agentUserId != null && !agentUserId.isEmpty()) {
            return userService.getByMobile(agentUserId);
        }
        String bindCode = (String) params.getOrDefault("voteBindCode", params.get("bindCode"));
        if (bindCode == null || bindCode.isEmpty()) {
            return null;
        }
        UserBind userBind = userBindService.getByBindCode(bindCode);
        if (userBind == null || userBind.getUserId() == null) {
            return null;
        }
        return userService.getById(userBind.getUserId());
    }

    /**
     * 记录投标流水（使用银行流水号做幂等）
     */
    private void recordInvestFlow(User user, BigDecimal amount, Lend lend,
                                  LocalDateTime now, String bankTransNo) {
        TransFlow flow = new TransFlow();
        flow.setUserId(user.getId());
        flow.setUserName(user.getName() != null ? user.getName() : user.getMobile());
        // 优先用银行流水号做 trans_no（回调幂等），没有则本地生成
        String transNo = (bankTransNo != null && !bankTransNo.isEmpty())
                ? bankTransNo : LendNoUtils.getTransNo();
        flow.setTransNo(transNo);
        flow.setTransType(TransTypeEnum.INVEST.getCode());
        flow.setTransTypeName(TransTypeEnum.INVEST.getDesc());
        flow.setTransAmount(amount);
        flow.setMemo("投标 " + lend.getLendNo());
        flow.setCreateTime(now);
        flow.setUpdateTime(now);
        transFlowService.save(flow);
        userAccountService.updateBalance(user.getId(), TransTypeEnum.INVEST, amount);
    }

    /**
     * 计算预期收益（简单估算）
     * 预期收益 = 投资金额 × 年化利率÷100 × 期限(月)÷12
     * 精度策略：中间运算 10 位，输出 2 位 HALF_UP，避免末位精度损失
     */
    private BigDecimal calcExpectAmount(BigDecimal amount, BigDecimal yearRate, Integer period) {
        if (amount == null || yearRate == null || period == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        // 全程保持中间 10 位精度，最后统一保留 2 位
        BigDecimal ratio = yearRate
                .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(period))
                .divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
        BigDecimal expect = amount.multiply(ratio);
        return expect.setScale(2, RoundingMode.HALF_UP);
    }
}