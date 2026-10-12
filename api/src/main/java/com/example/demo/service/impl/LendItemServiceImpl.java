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
import com.example.demo.enums.InvestorAuthStatusEnum;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.enums.TransStatusEnum;
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
import com.example.demo.utils.ExpectReturnUtil;
import com.example.demo.utils.LendNoUtils;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.wwfinance.common.utils.BankSignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        // 投资能力的两种合法来源，满足其一即可投标：
        //   ① 注册时选择了"投资者"（user_type==1）—— 存量 34 个未做投资人认证的账号走的就是这条，
        //      若直接收紧会让它们突然不能投标，故保留。
        //   ② 完成了投资人实名认证（invest_auth_status==2）—— 前端认证页明确写着
        //      "投资人认证（认证后可参与投标）"，这是对用户作出的承诺。
        // 原实现只认 ①，导致「注册成借款人 → 补做投资人认证 → 仍被拒」，与借款侧
        // 看 borrow_auth_status（BorrowInfoServiceImpl:68）的做法也不对称。
        boolean investorByUserType = UserTypeEnum.INVESTOR.getCode().equals(user.getUserType());
        boolean investorByAuth = InvestorAuthStatusEnum.AUTHED.getCode().equals(user.getInvestAuthStatus());
        if (!investorByUserType && !investorByAuth) {
            throw new ResourceNotFoundException(500, "你不是投资人，请先完成投资人实名认证");
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

        // 4.6 【P9-60】投资人侧《居间服务协议》：约定平台仅提供信息撮合、不兜底不担保，
        //     并作为收取撮合服务费的依据。用户级一次性协议，首次投标签一次即可。
        //     放在《出借协议》之后：先让用户签"这笔借贷"的协议，再补"与平台的关系"协议，
        //     提示语顺序更自然（模板缺失时该方法降级放行，不会把投标卡死）。
        contractService.assertUserAgreementSigned(ContractTypeEnum.SERVICE, userId);

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
        //
        //    【P0-1】必须用「统一可用额」而不是裸银行余额：
        //    银行 lockAccount（投标冻结）不校验余额，并发两笔投标都用同一份余额快照就能把账户扣成负数。
        //    getAvailableBalance = 银行余额 − 在途(提现+投标+还款)，并会在本次落一条处理中流水占位。
        BigDecimal available = bankAccountSyncService.getAvailableBalance(userId);
        BalanceGuard.require("", available, amount);

        // 7. 冻结由银行执行，平台不操作真实资金：
        //    银行回调时冻结投资人资金，平台仅记录投标记录（见 handleInvestNotify）。

        // 7.5 【P0-3】受益人（借款人）必须先绑卡，且要把它的银行账号传给银行。
        //
        //     银行放款（UserInvestServiceImpl.makeLoan）是把钱划到
        //     user_invest.benefit_bind_code 上的。而这个字段银行并不自己算 ——
        //     它由投标表单绑定进 UserInvest 对象。平台此前<b>从不传</b>这个参数，
        //     于是银行侧 78 条投资记录里 11 条 benefit_bind_code 存成了 "0"，
        //     makeLoan 执行 transferAccounts("0", ΣvoteAmt) 时对不上账户直接抛异常，
        //     银行渲染"出错了"页面 —— 放款永远成功不了。这是「满标不放款」的最后一环。
        //
        //     顺带解决另一个困境：借款人没绑卡时资金照样能被冻结，
        //     结果标投满了、放款却卡死，资金只能等 3 天宽限期自动退资。
        //     这里前置拦住，比事后退资干净得多。
        UserBind borrowerBind = userBindService.getByUserId(lend.getUserId());
        if (borrowerBind == null || !BindStatusEnum.BOUND.getCode().equals(borrowerBind.getStatus())) {
            throw new ResourceNotFoundException(500, "借款人尚未完成绑卡，该标的暂不可投标");
        }

        // 8. 组装旺旺银行投标参数并签名
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", HfbConst.AGENT_ID);
        String investBillNo = LendNoUtils.getLendItemNo();
        paramMap.put("agentUserId", userBind.getMobile());
        paramMap.put("voteBindCode", userBind.getBindCode());
        paramMap.put("benefitBindCode", borrowerBind.getBindCode());
        paramMap.put("agentBillNo", investBillNo);
        paramMap.put("voteAmt", amount);
        paramMap.put("votePrizeAmt", "0");
        paramMap.put("voteFeeAmt", "0");
        paramMap.put("agentProjectCode", lendId);
        paramMap.put("agentProjectName", lend.getTitle());
        paramMap.put("projectAmt", lend.getAmount().toString());
        paramMap.put("returnUrl", HfbConst.returnUrl());
        paramMap.put("notifyUrl", HfbConst.INVEST_NOTIFY_URL);
        paramMap.put("timestamp", new Date().getTime());
        paramMap.put("sign", BankSignUtil.getSign(paramMap));

        // 【P0-1】落一条「处理中」投标流水占位，占用该用户可用余额，
        // 防止并发第二笔投标再用同一份余额快照（银行扣款无余额校验，会被扣成负数）。
        // 银行回调到达后由 handleInvestNotify 结算为成功/失败并释放占用。
        transFlowService.savePending(userId, TransTypeEnum.INVEST, amount,
                "投标 " + lend.getLendNo(), investBillNo);

        log.info("投标表单生成，userId={}, lendId={}, amount={}, agentBillNo={}",
                userId, lendId, amount, investBillNo);
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
        //    【P0-1】必须同时把在途占位流水置为失败，否则这笔钱会被永久占住、用户再也用不了。
        if (!BankNotifyStatusEnum.isSuccess(status)) {
            log.info("投标回调银行状态非成功，status={}", status);
            transFlowService.settlePending(transNo, false);
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
        //    ⚠️ 必须用 existsSucceededByTransNo（只认"成功"），不能用 existsByTransNo。
        //    【P0-1】投标时已按同一 agentBillNo 落了一条「处理中」流水占位，
        //    若这里把处理中也算作"已入账"，回调会被误判成重复通知直接吞掉 ——
        //    那笔投标永远停在处理中，投资记录也永远不生成。
        //    也不能把"失败"算作已入账：超时兜底（expireStalePending）会先把占位置为失败，
        //    此时迟到的成功回调仍须能被结算入账。
        if (transNo != null && transFlowService.existsSucceededByTransNo(transNo)) {
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
        // 预期收益 = 还款计划里各期利息之和（与借款人实际支付、投资人实际收到的利息完全一致）。
        // 改造前这里是单利 amount×rate×period/12，1000 万/8%/12 期会显示 80 万，
        // 而实际等额本息只有约 43.86 万 —— 投资人看到的"预期收益"是拿不到的钱。
        BigDecimal expectAmount = ExpectReturnUtil.expectedInterest(
                amount, lend.getLendYearRate(), lend.getPeriod(), lend.getReturnMethod());
        lendItem.setExpectAmount(expectAmount);
        lendItem.setStatus(0);
        lendItem.setCreateTime(now);
        lendItem.setUpdateTime(now);
        this.save(lendItem);

        // 投资成功通知（给投资人）
        messageService.sendInvestNotice(user.getId(), amount, lend.getTitle());

        // 12. 检查是否满标
        // 【P4-20】invest_amount / amount 均为 decimal(10,2) NULL，且 atomicInvest 是
        // `invest_amount = invest_amount + ?`（NULL 加任何数仍为 NULL）——
        // 一条脏数据即让这行直接 NPE：投资人资金已被银行冻结，平台却不入账、不生成满标记。
        // 统一用 nvl() 兜底再比较。
        Lend updatedLend = lendService.getById(lendId);
        if (updatedLend == null) {
            log.warn("投标回调后重新查询标的为空，跳过满标判定，lendId={}", lendId);
            return true;
        }
        BigDecimal invested = nvl(updatedLend.getInvestAmount());
        BigDecimal targetAmount = nvl(updatedLend.getAmount());
        if (invested.compareTo(targetAmount) >= 0) {
            updatedLend.setStatus(LendStatusEnum.COMPLETED.getCode());
            // 满标时刻 = 3 个自然日签署宽限期的起算点，只在此刻写一次。
            // 必须显式设置：MyBatis-Plus 默认只更新非 null 字段，但此处若不设，
            // 定时任务就拿不到起算点，会自动跳过该标的 → 资金永久悬停。
            updatedLend.setCompleteTime(now);
            updatedLend.setUpdateTime(now);
            lendService.updateById(updatedLend);
            // 满标后同步把该标的全部投资记录置为满标状态（否则撤标校验的死分支永远进不去）
            // 【bug ③】必须排除已撤标（status=1）的记录：改造前是无条件 set(status,2)，
            // 一笔已经撤标、资金已退回投资人的记录会被重新标成"满标持有"，
            // 于是进入后续的放款分摊与还款分摊 —— 平台凭空多出一份不存在的债权份额，
            // 该份额没有对应的银行资金，直接破坏 Σ投资份额 ≡ 标的金额 这一恒等式。
            this.update(new LambdaUpdateWrapper<LendItem>()
                    .eq(LendItem::getLendId, lendId)
                    .ne(LendItem::getStatus, 1)
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
            BigDecimal leftAmount = targetAmount.subtract(invested);
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

        BigDecimal amount = nvl(lendItem.getInvestAmount());

        // 【bug ④】先扣减标的已投金额，**再**做任何有副作用的动作。
        // 改造前是 `lend.setInvestAmount(getInvestAmount() - amount); updateById(lend);`
        // 的"读-改-写"：并发撤标会互相覆盖，导致 invest_amount 永久偏高（详见 LendItemMapper）。
        // 改为数据库原子自减，并且放在最前面 —— 失败即抛异常，此时尚未回补镜像、
        // 尚未落退款流水，"撤标失败" 是一条零副作用的干净失败（@Transactional 再兜一层）。
        int cancelRows = baseMapper.atomicCancelInvest(lendItem.getLendId(), amount);
        if (cancelRows == 0) {
            throw new ResourceNotFoundException(500, "撤标失败：标的已满标/已放款或已投金额不足，请刷新后重试");
        }

        // 撤回：【P4-19】翻转「已撤标」+ 回补平台镜像余额 + 落一条退款流水。
        // 银行侧依据冻结关系自动解冻，平台侧镜像必须在同一事务内对称加回，
        // 否则任一次撤标都会让该投资人的镜像余额永久偏低（详见 refundInvestment）。
        this.refundInvestment(lendItem, "撤标退款 " + lend.getLendNo());

        // 原子更新后重新查询，Redis 剩余额度必须基于数据库最新值，不能再用内存里的旧对象
        Lend refreshed = lendService.getById(lendItem.getLendId());
        String leftKey = LEFT_AMOUNT_KEY + lendItem.getLendId();
        BigDecimal leftAmount = nvl(refreshed == null ? null : refreshed.getAmount())
                .subtract(nvl(refreshed == null ? null : refreshed.getInvestAmount()));
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
    @Transactional(rollbackFor = Exception.class)
    public boolean refundInvestment(LendItem lendItem, String memo) {
        if (lendItem == null || lendItem.getId() == null) {
            return false;
        }
        // 幂等闸门：只有「投标中(0) / 满标待放款(2)」需要退资。
        // 这里用**数据库原子 CAS**（status IN (0,2) → 1）而不是"读内存状态 → updateById"：
        // 后者在「用户点两次撤标」或「用户撤标 与 下架/流标批量退资 并发」时两道校验会同时放行，
        // 同一笔投资被退款两次（镜像多加一次钱 + 多一条退款流水），而银行只解冻一次。
        // 满标时本类会把全部投资记录统一置为 2，故以 status in (0,2) 为准。
        int flipped = baseMapper.atomicMarkCancelled(lendItem.getId());
        if (flipped == 0) {
            log.info("投资记录已撤标或状态不可退资，跳过重复退资，lendItemId={}, status={}",
                    lendItem.getId(), lendItem.getStatus());
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        // 同步内存对象，供调用方判断与日志使用（数据库已置为 1）
        lendItem.setStatus(1);
        lendItem.setUpdateTime(now);

        BigDecimal amount = nvl(lendItem.getInvestAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            // 脏数据（金额为 NULL/0）：没有对应的镜像扣减，无钱可退，只翻状态即可
            log.warn("退资金额非法，仅翻转状态不回补镜像，lendItemId={}, amount={}",
                    lendItem.getId(), lendItem.getInvestAmount());
            return true;
        }
        refundInvestorMirror(lendItem.getInvestUserId(), amount, memo, now);
        return true;
    }

    /**
     * 【P4-19】回补平台镜像余额，并落一条退款流水。
     *
     * <p>投标回调 {@code recordInvestFlow} 执行的是 {@code updateBalance(user, INVEST, amount)}
     * （走 {@code UserAccountMapper.deductAmount}，即"可用额 −amount"），
     * 因此退资必须走对称的加钱路径，否则任一「用户撤标 / 流标 / 下架 / 超期未签退资」
     * 之后，该投资人的 {@code user_account.amount} 都会<b>永久偏低</b>
     * —— 银行侧已解冻，平台侧没有加回。连带影响是
     * {@code RecommendServiceImpl} 读该镜像做"可投能力"过滤时会误判余额不足。</p>
     *
     * <p>流水沿用 {@code RETURN(4)}（"投资回款"）类型：与银行侧"解冻退回"是同一笔资金回退，
     * 前端 {@code TRANS_TYPE_META} 已按收入方向渲染；退款原因写进 {@code memo}
     * （撤标 / 流标 / 下架），对账时按备注逐笔核对。若后续统计口径需要区分，再单开枚举码。</p>
     *
     * <p><b>不吞异常</b>：镜像回补失败必须让外层事务整体回滚，否则会留下
     * "钱没退、状态却已撤"的脏数据（参见 P4-43 的反面教材）。</p>
     */
    private void refundInvestorMirror(Long userId, BigDecimal amount, String memo, LocalDateTime now) {
        User user = userService.getById(userId);
        TransFlow flow = new TransFlow();
        flow.setUserId(userId);
        flow.setUserName(user != null
                ? (user.getName() != null ? user.getName() : user.getMobile()) : "");
        flow.setTransNo(LendNoUtils.getTransNo());
        flow.setTransType(TransTypeEnum.RETURN.getCode());
        flow.setTransTypeName(TransTypeEnum.RETURN.getDesc());
        flow.setTransAmount(amount);
        flow.setStatus(TransStatusEnum.SUCCESS.getCode());
        flow.setMemo(memo);
        flow.setCreateTime(now);
        flow.setUpdateTime(now);
        transFlowService.save(flow);

        userAccountService.updateBalance(userId, TransTypeEnum.RETURN, amount);
        log.info("退资回补平台镜像成功，userId={}, amount={}, memo={}", userId, amount, memo);
    }

    /**
     * 【P4-20】金额判空：库中 {@code lend.invest_amount} / {@code lend_item.invest_amount}
     * 均为 {@code decimal(10,2) NULL}，统一按 0 处理，避免 NULL 参与运算或直接解引用。
     */
    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    public List<LendItem> listByLendId(Long lendId) {
        return this.list(new LambdaQueryWrapper<LendItem>()
                .eq(LendItem::getLendId, lendId)
                .orderByDesc(LendItem::getInvestTime));
    }

    @Override
    public boolean updateExpectAmount(Long lendItemId, BigDecimal expectAmount) {
        if (lendItemId == null) {
            return false;
        }
        return baseMapper.updateExpectAmount(lendItemId, expectAmount) > 0;
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
        // 优先用银行流水号做 trans_no（回调幂等），没有则本地生成
        String transNo = (bankTransNo != null && !bankTransNo.isEmpty())
                ? bankTransNo : LendNoUtils.getTransNo();

        // 【P0-1】投标发起时已按同一单号落了一条「处理中」在途流水（用于占用可用余额）。
        // 回调成功就地把那条置为成功，避免同一笔投标留下两条流水。
        transFlowService.settlePending(transNo, true);
        if (transFlowService.existsByTransNo(transNo)) {
            userAccountService.updateBalance(user.getId(), TransTypeEnum.INVEST, amount);
            return;
        }

        // 没有在途占位（银行未回带 agentBillNo 等场景）→ 补一条成功流水
        TransFlow flow = new TransFlow();
        flow.setUserId(user.getId());
        flow.setUserName(user.getName() != null ? user.getName() : user.getMobile());
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
}