package com.example.demo.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.Bank.FormHelper;
import com.example.demo.Bank.HfbConst;
import com.example.demo.Bank.RequestHelper;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.entity.LendItemReturn;
import com.example.demo.entity.LendReturn;
import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.enums.BankNotifyStatusEnum;
import com.example.demo.enums.BindStatusEnum;
import com.example.demo.enums.ContractBizTypeEnum;
import com.example.demo.enums.ContractTypeEnum;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.mapper.LendItemReturnMapper;
import com.example.demo.mapper.LendReturnMapper;
import com.example.demo.service.LendItemService;
import com.example.demo.service.BankAccountSyncService;
import com.example.demo.service.LendReturnService;
import com.example.demo.service.LendService;
import com.example.demo.service.MessageService;
import com.example.demo.service.TransFlowService;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.example.demo.utils.BalanceGuard;
import com.example.demo.utils.LendNoUtils;
import com.example.demo.utils.ReturnPlanCalculator;
import com.example.demo.utils.ReturnPlanCalculator.PeriodPlan;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.wwfinance.common.utils.BankSignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 还款 Service 实现
 */
@Slf4j
@Service
public class LendReturnServiceImpl extends ServiceImpl<LendReturnMapper, LendReturn> implements LendReturnService {

    /** 金额格式：正数，最多两位小数 */
    private static final String AMOUNT_REGEX = "\\d+(\\.\\d{1,2})?";

    /**
     * 银行<b>同步</b>放款接口（{@code /userInvest/AgreeAccountLendProject}）的成功码。
     *
     * <p>注意它和异步回调那套不一样：异步回调（绑卡/充值/提现/投标）用 {@code "1"} 或 {@code "0001"}，
     * 而放款同步接口返回 {@code resultCode=0000}。此前平台只认 {@code "1"}，
     * 即便有人把银行应答喂进 {@code handleMakeLoanNotify} 也会被判成"非成功"静默丢弃。
     */
    private static final String BANK_MAKE_LOAN_SUCCESS_CODE = "0000";

    @Autowired
    private LendService lendService;

    @Autowired
    private LendItemService lendItemService;

    @Autowired
    private LendItemReturnMapper lendItemReturnMapper;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private BankAccountSyncService bankAccountSyncService;

    @Autowired
    private UserService userService;

    @Autowired
    private TransFlowService transFlowService;

    @Autowired
    private UserBindService userBindService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private com.example.demo.service.UserIntegralService userIntegralService;

    @Autowired
    private com.example.demo.service.LendSettleRecordService lendSettleRecordService;

    @Autowired
    private com.example.demo.service.ContractService contractService;

    /**
     * 从银行回调参数中解析用户。
     * 放款/还款回调只回传 bindCode，不传 agentUserId；按 bindCode 反查绑卡记录再取用户。
     */
    private User resolveNotifyUser(Map<String, Object> params) {
        String agentUserId = (String) params.get("agentUserId");
        if (agentUserId != null && !agentUserId.isEmpty()) {
            return userService.getByMobile(agentUserId);
        }
        String bindCode = (String) params.get("bindCode");
        if (bindCode == null || bindCode.isEmpty()) {
            return null;
        }
        UserBind userBind = userBindService.getByBindCode(bindCode);
        if (userBind == null || userBind.getUserId() == null) {
            return null;
        }
        return userService.getById(userBind.getUserId());
    }

    /** 逾期罚息倍数（在原利率基础上上浮 50%） */
    private static final BigDecimal PENALTY_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal DAYS_OF_YEAR = new BigDecimal("365");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** 罚息上限：不超过逾期本金的 100% */
    private static final BigDecimal PENALTY_CAP_RATIO = new BigDecimal("1.0");
    /** 投资人占比的内部计算精度 */
    private static final int RATIO_SCALE = 10;

    @Override
    public String buildMakeLoanForm(Long lendId, Long adminId) {
        // 复用同步放款的校验 + 参数组装，保证两条通道参数完全一致
        return FormHelper.buildForm(HfbConst.MAKE_LOAD_URL, prepareMakeLoanParams(lendId, adminId));
    }

    /**
     * 放款前置校验 + 组装银行参数（同步/异步两条通道共用）。
     *
     * <p><b>两个必传字段是踩过坑才加的：</b>
     * <ul>
     *   <li>{@code mchFee} —— 银行 {@code UserInvestServiceImpl.makeLoan()} 里直接
     *       {@code new BigDecimal((String) map.get("mchFee"))}，缺参就是 NPE，银行 500。</li>
     *   <li>{@code agentBillNo} —— 银行会原样回传，平台用它做放款流水号与幂等键；
     *       不传的话流水号为空，事后无法对账。</li>
     * </ul>
     */
    private Map<String, Object> prepareMakeLoanParams(Long lendId, Long adminId) {
        // 1. 校验标的
        Lend lend = lendService.getById(lendId);
        if (lend == null) {
            throw new ResourceNotFoundException(500, "标的不存在");
        }
        if (!LendStatusEnum.COMPLETED.getCode().equals(lend.getStatus())) {
            throw new ResourceNotFoundException(500, "标的不是满标待放款状态，无法放款");
        }

        // 2. 幂等：已有还款计划则不重复放款
        long exists = this.count(new LambdaQueryWrapper<LendReturn>()
                .eq(LendReturn::getLendId, lendId));
        if (exists > 0) {
            throw new ResourceNotFoundException(500, "标的已放款，请勿重复操作");
        }

        // 2.5 签署关口三：借款人未签《放款确认书》不予放款。
        //     合同在标的发布（publish）时就已按同一标的一并生成，此处只做校验。
        contractService.assertSigned(ContractBizTypeEnum.LEND, lendId,
                ContractTypeEnum.PAYMENT, lend.getUserId(), null);

        // 3. 校验借款人绑卡
        User borrower = userService.getById(lend.getUserId());
        if (borrower == null) {
            throw new ResourceNotFoundException(500, "借款人不存在");
        }
        UserBind userBind = userBindService.getByUserId(lend.getUserId());
        if (userBind == null || !BindStatusEnum.BOUND.getCode().equals(userBind.getStatus())) {
            throw new ResourceNotFoundException(500, "借款人未绑卡，无法放款");
        }
        userBind.setMobile(borrower.getMobile());

        // 4. 组装旺旺银行放款参数并签名
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", HfbConst.AGENT_ID);
        paramMap.put("agentUserId", userBind.getMobile());
        paramMap.put("bindCode", userBind.getBindCode());
        paramMap.put("voteAmt", lend.getAmount());
        paramMap.put("agentProjectCode", lendId);
        paramMap.put("agentProjectName", lend.getTitle());
        // 商户手续费：银行侧 new BigDecimal(mchFee) 无判空，必须显式传 "0"（平台当前不收服务费）
        paramMap.put("mchFee", BigDecimal.ZERO.toPlainString());
        paramMap.put("agentBillNo", buildMakeLoanBillNo(lendId));
        // 放款走服务端同步 JSON（AdminLendController#makeLoan → makeLoanSync），
        // 银行不会拿 returnUrl 做浏览器跳转，此处仅保持与其他 5 条链路一致。
        paramMap.put("returnUrl", HfbConst.returnUrl());
        paramMap.put("notifyUrl", HfbConst.MAKE_LOAD_NOTIFY_URL);
        paramMap.put("timestamp", new Date().getTime());
        paramMap.put("sign", BankSignUtil.getSign(paramMap));

        log.info("放款参数就绪，lendId={}, borrowerId={}, amount={}, adminId={}",
                lendId, lend.getUserId(), lend.getAmount(), adminId);

        // 放款发起留痕：记录是谁发起的放款。
        // 注意 payment_time 是"银行确认划转、平台落账"的时间（bookMakeLoan 里写），
        // 与这里的操作时间语义不同，刻意不在此处写，便于区分"已发起/已入账"。
        Lend audit = new Lend();
        audit.setId(lendId);
        audit.setPaymentAdminId(adminId);
        audit.setUpdateTime(LocalDateTime.now());
        lendService.updateById(audit);

        return paramMap;
    }

    /**
     * 生成放款流水号：MAKELoan + yyyyMMdd + HHmmss + 标的ID后三位。
     * 与银行历史流水（如 MAKELoan20260928160000001）同格式，便于对账。
     */
    private String buildMakeLoanBillNo(Long lendId) {
        String stamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        return "MAKELoan" + stamp + String.format("%03d", lendId % 1000);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> makeLoanSync(Long lendId, Long adminId) {
        Map<String, Object> paramMap = prepareMakeLoanParams(lendId, adminId);
        String agentBillNo = (String) paramMap.get("agentBillNo");
        BigDecimal voteAmt = new BigDecimal(String.valueOf(paramMap.get("voteAmt")));

        log.info("同步放款请求银行，lendId={}, agentBillNo={}, voteAmt={}", lendId, agentBillNo, voteAmt);
        JSONObject resp = RequestHelper.sendFormRequest(paramMap, HfbConst.MAKE_LOAD_URL);

        Map<String, Object> result = new HashMap<>();
        result.put("lendId", lendId);
        result.put("agentBillNo", agentBillNo);
        result.put("bankResponse", resp);

        if (resp == null) {
            log.error("同步放款银行无应答，lendId={}", lendId);
            result.put("success", false);
            result.put("message", "银行无应答，请确认存管银行服务（默认 9090）可用");
            return result;
        }

        String resultCode = resp.getString("resultCode");
        result.put("resultCode", resultCode);
        result.put("resultMsg", resp.getString("resultMsg"));

        // 银行放款接口是同步返回，成功码是 "0000"（不是异步回调那套 "1"/"0001"）
        if (!BANK_MAKE_LOAN_SUCCESS_CODE.equals(resultCode)) {
            log.warn("银行放款未成功，lendId={}, resultCode={}, resultMsg={}",
                    lendId, resultCode, resp.getString("resultMsg"));
            result.put("success", false);
            result.put("message", "银行放款失败：" + resp.getString("resultMsg"));
            return result;
        }

        // 银行已划转 → 平台落账（与异步回调共用同一段逻辑）
        boolean booked = bookMakeLoan(lendId, agentBillNo, voteAmt);
        result.put("success", booked);
        result.put("message", booked
                ? "放款成功，资金已划转至借款人，还款计划已生成"
                : "银行已划转，但平台入账未生效（标的可能已放款），请查看日志");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleMakeLoanNotify(Map<String, Object> params) {
        // 1. 验签
        if (!BankSignUtil.isSignEquals(params)) {
            log.warn("放款回调验签失败，params={}", params);
            return false;
        }

        String agentUserId = (String) params.get("agentUserId");
        String amountStr = (String) params.getOrDefault("amount", params.get("voteAmt"));
        String transNo = (String) params.getOrDefault("transNo", params.get("agentBillNo"));
        String status = (String) params.getOrDefault("status", params.get("resultCode"));
        String projectIdStr = (String) params.getOrDefault("projectId", params.get("agentProjectCode"));

        if ("0001".equals(status) || BANK_MAKE_LOAN_SUCCESS_CODE.equals(status)) {
            status = BankNotifyStatusEnum.SUCCESS.getCode();
        }

        // 2. 银行侧处理失败：无需重试，应答 success
        if (!BankNotifyStatusEnum.isSuccess(status)) {
            log.info("放款回调银行状态非成功，status={}", status);
            return true;
        }

        // 3. 金额格式校验
        if (amountStr == null || !amountStr.matches(AMOUNT_REGEX)) {
            log.warn("放款回调金额非法，amount={}", amountStr);
            return false;
        }

        // 4. 查询借款人（银行回调只回传 bindCode，不传 agentUserId）
        User borrower = resolveNotifyUser(params);
        if (borrower == null) {
            log.warn("放款回调借款人不存在，agentUserId={}, bindCode={}",
                    agentUserId, params.get("bindCode"));
            return false;
        }

        // 5. 幂等：同一银行流水号不重复入账
        if (transNo != null && transFlowService.existsByTransNo(transNo)) {
            log.info("放款回调重复通知，直接确认，transNo={}", transNo);
            return true;
        }

        // 6. 解析标的ID
        Long lendId;
        try {
            lendId = Long.valueOf(projectIdStr);
        } catch (NumberFormatException e) {
            log.warn("放款回调projectId非法，projectId={}", projectIdStr);
            return false;
        }

        return bookMakeLoan(lendId, transNo, new BigDecimal(amountStr));
    }

    /**
     * 放款落账：二次校验 → 幂等 → 生成借款人还款计划 + 投资人收益计划 → 记放款流水
     * → 借款积分 → 标的转「还款中」→ 站内信通知借款人。
     *
     * <p>异步回调 {@link #handleMakeLoanNotify(Map)} 与同步放款 {@link #makeLoanSync(Long, Long)}
     * <b>共用</b>本方法，两条通道落账口径完全一致，避免"回调口径/同步口径"分叉。
     *
     * @param lendId  标的ID
     * @param transNo 银行流水号（幂等键，可为空）
     * @param voteAmt 银行实际划转金额
     * @return true=已落账（含"本就已落账"的幂等情形）；false=标的不存在
     */
    private boolean bookMakeLoan(Long lendId, String transNo, BigDecimal voteAmt) {
        LocalDateTime now = LocalDateTime.now();
        log.info("放款落账开始，lendId={}, transNo={}, voteAmt={}", lendId, transNo, voteAmt);

        // 二次校验标的（参数校验与落账之间状态可能已变）
        Lend lend = lendService.getById(lendId);
        if (lend == null) {
            log.warn("放款落账标的不存在，lendId={}", lendId);
            return false;
        }
        if (!LendStatusEnum.COMPLETED.getCode().equals(lend.getStatus())) {
            log.warn("放款落账标的不在满标待放款状态，lendId={}, status={}", lendId, lend.getStatus());
            return true;
        }

        // 8. 幂等：已有还款计划则不重复放款
        long exists = this.count(new LambdaQueryWrapper<LendReturn>()
                .eq(LendReturn::getLendId, lendId));
        if (exists > 0) {
            log.info("放款落账标的已生成还款计划，跳过重复放款，lendId={}", lendId);
            return true;
        }

        LocalDate loanDate = LocalDate.now();

        // 9. 生成借款人还款计划
        List<PeriodPlan> plans = ReturnPlanCalculator.calc(
                lend.getAmount(),
                lend.getLendYearRate(),
                lend.getPeriod(),
                lend.getReturnMethod(),
                loanDate);

        List<LendReturn> returns = new ArrayList<>(plans.size());
        for (PeriodPlan p : plans) {
            LendReturn lr = new LendReturn();
            lr.setLendId(lendId);
            lr.setBorrowInfoId(lend.getBorrowInfoId());
            lr.setReturnNo(LendNoUtils.getReturnNo());
            lr.setUserId(lend.getUserId());
            lr.setAmount(lend.getAmount());
            lr.setBaseAmount(p.getBaseAmount());
            lr.setCurrentPeriod(p.getPeriod());
            lr.setLendYearRate(lend.getLendYearRate());
            lr.setReturnMethod(lend.getReturnMethod());
            lr.setPrincipal(p.getPrincipal());
            lr.setInterest(p.getInterest());
            lr.setTotal(p.getTotal());
            lr.setFee(BigDecimal.ZERO);
            lr.setReturnDate(p.getReturnDate());
            lr.setOverdue(false);
            lr.setLast(p.getPeriod() == plans.size());
            lr.setStatus(0);
            lr.setCreateTime(now);
            lr.setUpdateTime(now);
            returns.add(lr);
        }
        this.saveBatch(returns);

        // 10. 为每个投资人生成收益计划
        //
        // 【P4-53】分摊口径：**按投资占比切分期供**，不是"每个投资人独立跑一遍还款计划计算"。
        // 独立计算看着更"精确"，实际有两个坑：
        //   ① 各户四舍五入到分后加总，与整体算出的期供差 1 分（标的10：86.99+782.90=869.89 vs 869.88）；
        //   ② 更严重的是**末期**——末期本金取"剩余本金"，前面的舍入误差会累积，
        //      实测标的10 第12期偏差达 0.13 元，全倒挤给一户会让该投资人回款明显失真。
        // 改为按占比分摊后，各期（含末期）偏差都被压到 ≤ 0.01×户数，再由
        // balanceItemReturns() 倒挤补平，Σ 严格等于期供。
        List<LendItem> items = lendItemService.list(new LambdaQueryWrapper<LendItem>()
                .eq(LendItem::getLendId, lendId));
        List<LendItemReturn> itemReturns = new ArrayList<>();
        BigDecimal lendAmount = nvl(lend.getAmount());
        if (!items.isEmpty() && lendAmount.signum() > 0) {
            for (LendItem item : items) {
                BigDecimal ratio = nvl(item.getInvestAmount())
                        .divide(lendAmount, RATIO_SCALE, RoundingMode.HALF_UP);
                for (LendReturn lr : returns) {
                    BigDecimal p = money2(nvl(lr.getPrincipal()).multiply(ratio));
                    BigDecimal i = money2(nvl(lr.getInterest()).multiply(ratio));
                    LendItemReturn lir = new LendItemReturn();
                    lir.setLendReturnId(lr.getId());
                    lir.setLendItemId(item.getId());
                    lir.setLendId(lendId);
                    lir.setInvestUserId(item.getInvestUserId());
                    lir.setInvestAmount(item.getInvestAmount());
                    lir.setCurrentPeriod(lr.getCurrentPeriod());
                    lir.setLendYearRate(item.getLendYearRate());
                    lir.setReturnMethod(lend.getReturnMethod());
                    lir.setPrincipal(p);
                    lir.setInterest(i);
                    lir.setTotal(p.add(i));
                    lir.setFee(BigDecimal.ZERO);
                    lir.setReturnDate(lr.getReturnDate());
                    lir.setOverdue(false);
                    lir.setStatus(0);
                    lir.setCreateTime(now);
                    lir.setUpdateTime(now);
                    itemReturns.add(lir);
                }
            }
        }
        // 10.5 【P4-53】投资人回款明细倒挤：保证 Σ明细 ≡ 该期期供。
        //      明细是「每个投资人独立跑一遍 ReturnPlanCalculator」算出来的，
        //      各自四舍五入到分之后加总，与整体算出来的期供会差 1 分
        //      （实测标的10：86.99 + 782.90 = 869.89，而期供是 869.88）。
        //      银行 returnCommit 有强等式 voteFeeAmt + ΣtransitAmt == totalAmt，
        //      差 1 分就抛 RETURN_AMMOUNT_MORE_ERROR ⇒ 多投资人标的的还款必然失败。
        balanceItemReturns(itemReturns, returns);

        if (!itemReturns.isEmpty()) {
            itemReturns.forEach(lir -> lendItemReturnMapper.insert(lir));
        }

        // 11. 借款人入账（放款，银行处理）
        recordFlow(lend.getUserId(), TransTypeEnum.LOAN, lend.getAmount(),
                "标的放款 " + lend.getLendNo(), now, transNo);
        log.info("放款流水已记录，借款人 {} 应收到 {}，由银行处理入账",
                lend.getUserId(), lend.getAmount());

        // 11.1 借款成功累计积分（每借100元=1积分）
        int loanPoints = lend.getAmount().divide(new BigDecimal("100")).intValue();
        if (loanPoints > 0) {
            userIntegralService.addIntegral(lend.getUserId(), loanPoints, "借款成功累计积分");
        }

        // 12. 更新标的状态为还款中
        lend.setStatus(LendStatusEnum.REPAYING.getCode());
        lend.setPaymentTime(now);
        lend.setLendStartDate(loanDate);
        lend.setLendEndDate(loanDate.plusMonths(lend.getPeriod()));
        lend.setRealAmount(lend.getInvestAmount());
        lend.setUpdateTime(now);
        lendService.updateById(lend);

        log.info("标的 {} 放款落账处理成功，借款计划 {} 期，投资人收益明细 {} 条，transNo={}",
                lendId, returns.size(), itemReturns.size(), transNo);

        // 满标放款通知（给借款人，提示资金已入账+按时还款）
        messageService.sendMakeLoanNotice(lend.getUserId(), lend.getLendNo(), lend.getAmount());
        return true;
    }

    /**
     * 【P4-53】投资人回款明细倒挤：让「Σ投资人明细」严格等于「该期期供」。
     *
     * <p>为什么需要倒挤：明细是给<b>每个投资人独立跑一遍</b>
     * {@link ReturnPlanCalculator#calc} 算出来的（等额本息在数学上可加，
     * 但各自四舍五入到分之后就不一定了）。实测标的 10：
     * <pre>
     *   期供（整体算）      = 869.88
     *   明细（各自算）      = 86.99 + 782.90 = 869.89   ← 差 +0.01
     * </pre>
     * 银行 {@code returnCommit} 有强等式 {@code voteFeeAmt + ΣtransitAmt == totalAmt}，
     * 差 1 分就抛 {@code RETURN_AMMOUNT_MORE_ERROR} ⇒
     * <b>多投资人标的的还款必然失败，单投资人标的恰好不受影响</b>。
     *
     * <p>倒挤口径（与 {@link #buildRepayForm} 的兜底倒挤保持一致）：
     * <ul>
     *   <li>把本金差额、利息差额补到<b>投资金额最大</b>的那一户 —— 它的相对误差最小；</li>
     *   <li>只调 principal / interest，<b>total 由二者相加得出</b>，不独立舍入；</li>
     *   <li>倒挤后 Σprincipal ≡ 期供本金、Σinterest ≡ 期供利息、Σtotal ≡ 期供总额，三者同时成立。</li>
     * </ul>
     */
    private void balanceItemReturns(List<LendItemReturn> itemReturns, List<LendReturn> returns) {
        if (itemReturns == null || itemReturns.isEmpty() || returns == null || returns.isEmpty()) {
            return;
        }

        Map<Long, LendReturn> periodMap = new HashMap<>();
        for (LendReturn lr : returns) {
            if (lr.getId() != null) {
                periodMap.put(lr.getId(), lr);
            }
        }

        Map<Long, List<LendItemReturn>> byPeriod = new HashMap<>();
        for (LendItemReturn ir : itemReturns) {
            if (ir.getLendReturnId() == null) {
                continue;
            }
            byPeriod.computeIfAbsent(ir.getLendReturnId(), k -> new ArrayList<>()).add(ir);
        }

        for (Map.Entry<Long, List<LendItemReturn>> e : byPeriod.entrySet()) {
            LendReturn lr = periodMap.get(e.getKey());
            List<LendItemReturn> list = e.getValue();
            if (lr == null || list.isEmpty()) {
                continue;
            }

            BigDecimal sumPrincipal = BigDecimal.ZERO;
            BigDecimal sumInterest = BigDecimal.ZERO;
            for (LendItemReturn ir : list) {
                sumPrincipal = sumPrincipal.add(nvl(ir.getPrincipal()));
                sumInterest = sumInterest.add(nvl(ir.getInterest()));
            }

            BigDecimal diffPrincipal = nvl(lr.getPrincipal()).subtract(sumPrincipal);
            BigDecimal diffInterest = nvl(lr.getInterest()).subtract(sumInterest);
            if (diffPrincipal.signum() == 0 && diffInterest.signum() == 0) {
                continue;
            }

            LendItemReturn target = list.get(0);
            for (LendItemReturn ir : list) {
                if (nvl(ir.getInvestAmount()).compareTo(nvl(target.getInvestAmount())) > 0) {
                    target = ir;
                }
            }

            target.setPrincipal(nvl(target.getPrincipal()).add(diffPrincipal));
            target.setInterest(nvl(target.getInterest()).add(diffInterest));
            target.setTotal(nvl(target.getPrincipal()).add(nvl(target.getInterest())));

            log.warn("还款计划明细倒挤：lendReturnId={}, 第{}期, 本金差={}, 利息差={}, 补记到投资人{}",
                    lr.getId(), lr.getCurrentPeriod(), diffPrincipal, diffInterest,
                    target.getInvestUserId());
        }
    }

    /** null 安全：金额字段一律按 0 处理 */
    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 金额保留 2 位（四舍五入）；投资人分摊时逐笔舍入，余量由倒挤补平 */
    private static BigDecimal money2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String buildRepayForm(Long lendReturnId, Long userId) {
        // 1. 校验还款计划
        LendReturn lendReturn = this.getById(lendReturnId);
        if (lendReturn == null) {
            throw new ResourceNotFoundException(500, "还款计划不存在");
        }
        if (!lendReturn.getUserId().equals(userId)) {
            throw new ResourceNotFoundException(500, "只能偿还自己的借款");
        }
        if (lendReturn.getStatus() != null && lendReturn.getStatus() == 1) {
            throw new ResourceNotFoundException(500, "本期已还清，请勿重复还款");
        }

        // 2. 校验借款人绑卡
        User borrower = userService.getById(userId);
        if (borrower == null) {
            throw new ResourceNotFoundException(500, "用户不存在");
        }
        UserBind userBind = userBindService.getByUserId(userId);
        if (userBind == null || !BindStatusEnum.BOUND.getCode().equals(userBind.getStatus())) {
            throw new ResourceNotFoundException(500, "请先完成绑卡");
        }
        userBind.setMobile(borrower.getMobile());

        // 3. 应扣金额：逾期时含罚息
        boolean overdue = Boolean.TRUE.equals(lendReturn.getOverdue());
        BigDecimal payable = (overdue && lendReturn.getOverdueTotal() != null)
                ? lendReturn.getOverdueTotal()
                : nvl(lendReturn.getTotal());

        // 3.2 【P0-4】逾期时把"罚息"计进 voteFeeAmt。
        //     银行 returnCommit 校验 voteFeeAmt + ΣtransitAmt == totalAmt：
        //     逾期时 totalAmt = 本息 + 罚息，而投资人明细只摊本息，
        //     原先 voteFeeAmt 恒为 0 ⇒ 等式必然不成立 ⇒ 逾期还款在银行侧必失败。
        //     把罚息（= 应扣 − 期供本息）放进 voteFeeAmt，等式成立，
        //     且与代码注释"罚息部分归平台"的语义一致。
        BigDecimal voteFeeAmt;
        if (overdue && lendReturn.getOverdueTotal() != null) {
            voteFeeAmt = lendReturn.getOverdueTotal().subtract(nvl(lendReturn.getTotal()));
            if (voteFeeAmt.signum() < 0) {
                voteFeeAmt = BigDecimal.ZERO;
            }
        } else {
            voteFeeAmt = nvl(lendReturn.getFee());
        }

        // 3.5 余额闸门：余额不够就不许把单子递到银行去。
        //     原先缺这道校验，余额为 0 也能提交，银行扣不动、回调失败，
        //     用户端还会因为拿不到表单而静默显示"还款请求已提交"。
        // 【P0-1】必须用「统一可用额」而不是裸银行余额：
        // 银行 transferAccounts / lockAccount 都不校验余额，并发两笔还款会把账户扣成负数。
        BalanceGuard.require("本期还款", bankAccountSyncService.getAvailableBalance(userId), payable);

        Lend lend = lendService.getById(lendReturn.getLendId());

        // 4. 查询本期投资人回款明细（银行 returnCommit 需要 data 字段）
        List<LendItemReturn> itemReturns = lendItemReturnMapper.selectList(
                new LambdaQueryWrapper<LendItemReturn>()
                        .eq(LendItemReturn::getLendReturnId, lendReturnId)
                        .eq(LendItemReturn::getStatus, 0));

        // 5. 构造投资人回款明细列表（银行 data 字段）
        //    银行 returnCommit 会遍历此列表：给每个投资人转账 transitAmt，
        //    并校验 voteFeeAmt + sum(transitAmt) == totalAmt
        List<Map<String, Object>> detailList = new ArrayList<>();
        for (LendItemReturn ir : itemReturns) {
            UserBind investorBind = userBindService.getByUserId(ir.getInvestUserId());
            LendItem investItem = lendItemService.getById(ir.getLendItemId());
            Map<String, Object> detail = new HashMap<>();
            detail.put("voteBillNo", investItem != null ? investItem.getLendItemNo() : "");
            detail.put("toBindCode", investorBind != null ? investorBind.getBindCode() : "");
            detail.put("transitAmt", nvl(ir.getTotal()));
            detail.put("baseAmt", nvl(ir.getPrincipal()));
            detail.put("benifitAmt", nvl(ir.getInterest()));
            detailList.add(detail);
        }

        // 5.5 【P4-53】运行期兜底倒挤：保证银行强等式 voteFeeAmt + ΣtransitAmt == totalAmt 恒成立。
        //     新生成的还款计划已由 balanceItemReturns() 保证自洽，但**存量**计划
        //     （2026-10-08 前生成的）可能存在 1 分分厘差，若不在这里补平，
        //     那些期的还款会 100% 被银行拒绝。
        //     差额（通常 ±0.01）补到最后一户的 transitAmt，与生成期倒挤口径一致。
        BigDecimal sumTransit = BigDecimal.ZERO;
        for (Map<String, Object> detail : detailList) {
            Object amt = detail.get("transitAmt");
            sumTransit = sumTransit.add(amt instanceof BigDecimal
                    ? (BigDecimal) amt
                    : new BigDecimal(String.valueOf(amt)));
        }
        BigDecimal diff = payable.subtract(voteFeeAmt).subtract(sumTransit);
        if (diff.signum() != 0 && !detailList.isEmpty()) {
            Map<String, Object> last = detailList.get(detailList.size() - 1);
            BigDecimal adjusted = (last.get("transitAmt") instanceof BigDecimal
                    ? (BigDecimal) last.get("transitAmt")
                    : new BigDecimal(String.valueOf(last.get("transitAmt")))).add(diff);
            if (adjusted.signum() < 0) {
                throw new ResourceNotFoundException(500,
                        "还款金额分摊异常：倒挤后投资人回款为负，请检查还款计划 lendReturnId=" + lendReturnId);
            }
            last.put("transitAmt", adjusted);
            log.warn("还款表单金额倒挤：lendReturnId={}, 期供={}, 手续费={}, 明细原合计={}, 差额={} 已补到最后一户",
                    lendReturnId, payable, voteFeeAmt, sumTransit, diff);
        }

        // 6. 组装旺旺银行还款参数并签名
        //    银行 returnCommit 读取字段（字节码确认）：fromBindCode, totalAmt, voteFeeAmt, data
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", HfbConst.AGENT_ID);
        paramMap.put("agentUserId", userBind.getMobile());
        paramMap.put("fromBindCode", userBind.getBindCode());
        paramMap.put("totalAmt", payable);
        paramMap.put("voteFeeAmt", voteFeeAmt);
        paramMap.put("agentProjectCode", lendReturn.getLendId());
        // agentBatchNo 用还款计划ID：银行回调会原样回带（它不回带 agentBillNo），
        // 平台靠它把「处理中」占位流水结算成终态。
        String repayBatchNo = String.valueOf(lendReturnId);
        paramMap.put("agentBatchNo", repayBatchNo);
        paramMap.put("agentGoodsName", lend.getTitle());
        paramMap.put("data", com.alibaba.fastjson.JSON.toJSONString(detailList));
        paramMap.put("returnUrl", HfbConst.returnUrl());
        paramMap.put("notifyUrl", HfbConst.BORROW_RETURN_NOTIFY_URL);
        paramMap.put("timestamp", new Date().getTime());
        paramMap.put("sign", BankSignUtil.getSign(paramMap));

        // 【P0-1】落一条「处理中」还款流水占位，占用该用户可用余额。
        // 银行回调（agentBatchNo 回带）到达后由 recordFlow 结算为成功并释放占用。
        transFlowService.savePending(userId, TransTypeEnum.REPAY, payable,
                "还款 第" + lendReturn.getCurrentPeriod() + "期 " + lend.getLendNo(), repayBatchNo);

        log.info("还款表单生成，lendReturnId={}, userId={}, amount={}, principal={}, interest={}, investors={}",
                lendReturnId, userId, payable, lendReturn.getPrincipal(), lendReturn.getInterest(), detailList.size());
        return FormHelper.buildForm(HfbConst.BORROW_RETURN_URL, paramMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleRepayNotify(Map<String, Object> params) {
        // 1. 验签
        if (!BankSignUtil.isSignEquals(params)) {
            log.warn("还款回调验签失败，params={}", params);
            return false;
        }

        String agentUserId = (String) params.get("agentUserId");
        String amountStr = (String) params.getOrDefault("amount",
                params.getOrDefault("voteAmt", params.get("totalAmt")));
        String transNo = (String) params.getOrDefault("transNo", params.get("agentBillNo"));
        String status = (String) params.getOrDefault("status", params.get("resultCode"));
        String returnIdStr = (String) params.getOrDefault("returnId", params.get("agentBatchNo"));

        if ("0001".equals(status)) {
            status = BankNotifyStatusEnum.SUCCESS.getCode();
        }

        // 2. 银行侧处理失败：无需重试，应答 success
        //    【P0-1】必须释放还款发起时占住的可用额（银行不回带 agentBillNo，
        //    占位单号就是 agentBatchNo = 还款计划ID）。
        if (!BankNotifyStatusEnum.isSuccess(status)) {
            log.info("还款回调银行状态非成功，status={}", status);
            transFlowService.settlePending(
                    (String) params.getOrDefault("returnId", params.get("agentBatchNo")), false);
            return true;
        }

        // 3. 金额格式校验
        if (amountStr == null || !amountStr.matches(AMOUNT_REGEX)) {
            log.warn("还款回调金额非法，amount={}", amountStr);
            return false;
        }

        // 4. 查询借款人（银行回调只回传 bindCode，不传 agentUserId）
        User borrower = resolveNotifyUser(params);
        if (borrower == null) {
            // 银行还款回调可能不回传 agentUserId/bindCode，只有 agentBatchNo(=lendReturnId)
            // 通过 agentBatchNo 反查 lendReturn → userId → 借款人
            String batchNo = (String) params.getOrDefault("agentBatchNo", returnIdStr);
            if (batchNo != null) {
                try {
                    LendReturn lr = this.getById(Long.valueOf(batchNo));
                    if (lr != null && lr.getUserId() != null) {
                        borrower = userService.getById(lr.getUserId());
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (borrower == null) {
            log.warn("还款回调借款人不存在，agentUserId={}, bindCode={}, agentBatchNo={}",
                    agentUserId, params.get("bindCode"), params.get("agentBatchNo"));
            return false;
        }

        // 5. 幂等：同一银行流水号不重复入账
        if (transNo != null && transFlowService.existsByTransNo(transNo)) {
            log.info("还款回调重复通知，直接确认，transNo={}", transNo);
            return true;
        }

        // 6. 解析还款计划ID
        Long lendReturnId;
        try {
            lendReturnId = Long.valueOf(returnIdStr);
        } catch (NumberFormatException e) {
            log.warn("还款回调returnId非法，returnId={}", returnIdStr);
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal payable = new BigDecimal(amountStr);

        // 7. 二次校验还款计划（回调时状态可能已变）
        LendReturn lendReturn = this.getById(lendReturnId);
        if (lendReturn == null) {
            log.warn("还款回调还款计划不存在，lendReturnId={}", lendReturnId);
            return false;
        }
        if (!lendReturn.getUserId().equals(borrower.getId())) {
            log.warn("还款回调借款人与还款计划不匹配，lendReturnId={}, userId={}", lendReturnId, borrower.getId());
            return false;
        }
        if (lendReturn.getStatus() != null && lendReturn.getStatus() == 1) {
            log.info("还款回调本期已还清，跳过重复处理，lendReturnId={}", lendReturnId);
            return true;
        }

        // 8. 金额校验：回调金额不得低于应还本息。
        //    注意不能用逾期时的 overdueTotal 做等值断言——定时任务每天会按天数重算罚息，
        //    跨日重试的回调金额必然与最新值不一致，会导致用户钱已扣但还款计划永远不更新。
        //    因此只做下限校验，多出部分按罚息入账。
        boolean overdue = Boolean.TRUE.equals(lendReturn.getOverdue());
        BigDecimal expectedTotal = lendReturn.getTotal() != null ? lendReturn.getTotal() : BigDecimal.ZERO;
        if (payable.compareTo(expectedTotal) < 0) {
            log.warn("还款回调金额低于应还本息，回调={}, 应还本息={}", payable, expectedTotal);
            return false;
        }

        // 9. 扣减借款人余额（银行处理，平台仅记录流水）
        //    【P0-1】bankTransNo 传 returnIdStr（= agentBatchNo）而不是银行流水号：
        //    还款发起时占位的那条流水的 trans_no 就是它，用同一单号才能把占位结算掉，
        //    否则会既留着一条"处理中"永久占用、又新增一条成功流水。
        recordFlow(borrower.getId(), TransTypeEnum.REPAY, lendReturn.getTotal(),
                "归还借款第" + lendReturn.getCurrentPeriod() + "期 " + lendReturn.getReturnNo(), now, returnIdStr);
        // 逾期罚息单独记一条流水（罚息部分归平台，不参与投资人本息分摊）
        if (overdue) {
            BigDecimal penalty = payable.subtract(lendReturn.getTotal());
            if (penalty.compareTo(BigDecimal.ZERO) > 0) {
                recordFlow(borrower.getId(), TransTypeEnum.REPAY, penalty,
                        "第" + lendReturn.getCurrentPeriod() + "期逾期罚息", now, returnIdStr + "_PENALTY");
            }
        }
        log.info("还款流水已记录，银行将扣减借款人余额 {}", borrower.getId());

        // 10. 查询本期所有投资人收益明细并逐个回款
        Lend lend = lendService.getById(lendReturn.getLendId());
        List<LendItemReturn> itemReturns = lendItemReturnMapper.selectList(
                new LambdaQueryWrapper<LendItemReturn>()
                        .eq(LendItemReturn::getLendReturnId, lendReturnId)
                        .eq(LendItemReturn::getStatus, 0));
        for (LendItemReturn ir : itemReturns) {
            // 投资人回款入账由银行处理（平台不碰资金），平台仅更新回款状态和记录流水
            ir.setStatus(1);
            ir.setRealReturnTime(now);
            ir.setUpdateTime(now);
            lendItemReturnMapper.updateById(ir);
            recordFlow(ir.getInvestUserId(), TransTypeEnum.RETURN, ir.getTotal(),
                    "投资回款第" + ir.getCurrentPeriod() + "期", now, transNo + "_R" + ir.getId());
            // 回款到账通知（给投资人）。
            // 此前这里只给借款人发了还款通知，投资人账户进了钱却一条消息都没有 ——
            // 信息模块缺的正是这条"我该收到钱了"的提醒。
            messageService.sendInvestReturnNotice(ir.getInvestUserId(), ir.getCurrentPeriod(),
                    ir.getTotal(), lend != null ? lend.getTitle() : "您的投资标的");
        }

        // 11. 更新借款人还款计划
        lendReturn.setStatus(1);
        lendReturn.setRealReturnTime(now);
        lendReturn.setUpdateTime(now);
        this.updateById(lendReturn);

        // 12. 判断标的状态：全部结清→已完结；否则若标的处于逾期状态→恢复还款中
        long unpaid = this.count(new LambdaQueryWrapper<LendReturn>()
                .eq(LendReturn::getLendId, lendReturn.getLendId())
                .ne(LendReturn::getStatus, 1));
        if (unpaid == 0) {
            lend.setStatus(LendStatusEnum.SETTLED.getCode());
        } else if (LendStatusEnum.OVERDUE.getCode().equals(lend.getStatus())) {
            lend.setStatus(LendStatusEnum.REPAYING.getCode());
        }
        lend.setUpdateTime(now);
        lendService.updateById(lend);

        log.info("还款回调处理成功，lendReturnId={}, userId={}, amount={}, transNo={}",
                lendReturnId, borrower.getId(), payable, transNo);

        // 还款成功通知（给借款人，提示本期已扣款+关注后续计划）
        messageService.sendRepayNotice(borrower.getId(), lendReturn.getCurrentPeriod(), payable);

        // 标的全部结清：聚合本息罚息合计 + 写结清凭证 + 发结清通知 + 加守信积分
        //
        // 这里刻意**不包 try-catch**：此前整段被 catch(Exception) 吞掉，而 lend.status=SETTLED
        // 已在本方法前面于同一事务写入，于是产生"标的已结清、却没有结清凭证、积分也没加"
        // 的账目缺口；又因 lendReturn 已置已还 + transNo 已记账，回调重试会直接走幂等分支，
        // 该缺口永远无法自愈。
        // 凭证/通知/积分都是"结清"的组成部分，任一失败即整体回滚，交由银行重试重新处理，
        // 与上方 sendRepayNotice 的处理方式保持一致。
        if (unpaid == 0) {
            List<LendReturn> allReturns = this.list(new LambdaQueryWrapper<LendReturn>()
                    .eq(LendReturn::getLendId, lend.getId()));
            BigDecimal totalPrincipal = allReturns.stream()
                    .map(LendReturn::getPrincipal)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalInterest = allReturns.stream()
                    .map(LendReturn::getInterest)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalPenalty = allReturns.stream()
                    .map(lrx -> (lrx.getOverdueTotal() == null ? BigDecimal.ZERO
                            : lrx.getOverdueTotal().subtract(
                                    (lrx.getTotal() == null ? BigDecimal.ZERO : lrx.getTotal()))))
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            LocalDateTime settleTime = LocalDateTime.now();

            // --------只调用封装好的方法，不再new LendSettleRecord实体！！--------
            lendSettleRecordService.createSettleRecord(
                    lend.getId(),
                    lend.getLendNo(),
                    borrower.getId(),
                    totalPrincipal,
                    totalInterest,
                    totalPenalty,
                    settleTime
            );

            // 下面通知、加积分保留不变
            messageService.sendLendSettleNotice(borrower.getId(), lend.getLendNo(),
                    totalPrincipal, totalInterest);
            userIntegralService.addIntegral(borrower.getId(), 10, "标的结清守信加分");

            log.info("标的 {} 全部结清，调用createSettleRecord生成结清凭证，发结清通知 + 加 10 守信积分，借款人={}",
                    lend.getLendNo(), borrower.getId());
        }
        return true;
    }

    @Override
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public void repay(Long lendReturnId, Long userId) {
        // 1. 校验还款计划
        LendReturn lendReturn = this.getById(lendReturnId);
        if (lendReturn == null) {
            throw new ResourceNotFoundException(500, "还款计划不存在");
        }
        if (!lendReturn.getUserId().equals(userId)) {
            throw new ResourceNotFoundException(500, "只能偿还自己的借款");
        }
        if (lendReturn.getStatus() != null && lendReturn.getStatus() == 1) {
            throw new ResourceNotFoundException(500, "本期已还清，请勿重复还款");
        }

        LocalDateTime now = LocalDateTime.now();

        // 2. 应扣金额：逾期时含罚息（overdue_total = 应还本息 + 罚息）
        boolean overdue = Boolean.TRUE.equals(lendReturn.getOverdue());
        BigDecimal payable = (overdue && lendReturn.getOverdueTotal() != null)
                ? lendReturn.getOverdueTotal()
                : lendReturn.getTotal();

        // 3. 扣减借款人余额（银行处理，平台仅记录流水）
        recordFlow(userId, TransTypeEnum.REPAY, lendReturn.getTotal(),
                "归还借款第" + lendReturn.getCurrentPeriod() + "期 " + lendReturn.getReturnNo(), now, null);
        // 逾期罚息单独记一条流水（罚息部分归平台，不参与投资人本息分摊）
        if (overdue) {
            BigDecimal penalty = payable.subtract(lendReturn.getTotal());
            if (penalty.compareTo(BigDecimal.ZERO) > 0) {
                recordFlow(userId, TransTypeEnum.REPAY, penalty,
                        "第" + lendReturn.getCurrentPeriod() + "期逾期罚息", now, null);
            }
        }
        log.info("还款流水已记录，银行将扣减借款人余额 {}", userId);

        // 4. 查询本期所有投资人收益明细并逐个回款（银行处理）
        List<LendItemReturn> itemReturns = lendItemReturnMapper.selectList(
                new LambdaQueryWrapper<LendItemReturn>()
                        .eq(LendItemReturn::getLendReturnId, lendReturnId)
                        .eq(LendItemReturn::getStatus, 0));
        for (LendItemReturn ir : itemReturns) {
            recordFlow(ir.getInvestUserId(), TransTypeEnum.RETURN, ir.getTotal(),
                    "投资回款第" + ir.getCurrentPeriod() + "期", now, null);
            ir.setStatus(1);
            ir.setRealReturnTime(now);
            ir.setUpdateTime(now);
            lendItemReturnMapper.updateById(ir);
        }
        log.info("投资人回款流水已记录，银行将处理投资人入账");

        // 5. 更新借款人还款计划
        lendReturn.setStatus(1);
        lendReturn.setRealReturnTime(now);
        lendReturn.setUpdateTime(now);
        this.updateById(lendReturn);

        // 6. 判断标的状态：全部结清→已完结；否则若标的处于逾期状态→恢复还款中
        long unpaid = this.count(new LambdaQueryWrapper<LendReturn>()
                .eq(LendReturn::getLendId, lendReturn.getLendId())
                .ne(LendReturn::getStatus, 1));
        Lend lend = lendService.getById(lendReturn.getLendId());
        if (unpaid == 0) {
            lend.setStatus(LendStatusEnum.SETTLED.getCode());
        } else if (LendStatusEnum.OVERDUE.getCode().equals(lend.getStatus())) {
            lend.setStatus(LendStatusEnum.REPAYING.getCode());
        }
        lend.setUpdateTime(now);
        lendService.updateById(lend);
        if (unpaid == 0) {
            log.info("标的 {} 全部结清", lendReturn.getLendId());
        }
    }

    @Override
    public List<LendReturn> listByBorrower(Long userId) {
        return this.list(new LambdaQueryWrapper<LendReturn>()
                .eq(LendReturn::getUserId, userId)
                .orderByAsc(LendReturn::getCurrentPeriod));
    }

    @Override
    public List<LendReturn> listByLendId(Long lendId) {
        return this.list(new LambdaQueryWrapper<LendReturn>()
                .eq(LendReturn::getLendId, lendId)
                .orderByAsc(LendReturn::getCurrentPeriod));
    }

    @Override
    public List<LendItemReturn> listItemReturns(Long lendReturnId, Long userId) {
        LendReturn lendReturn = this.getById(lendReturnId);
        if (lendReturn == null) {
            throw new ResourceNotFoundException(404, "还款计划不存在");
        }
        LambdaQueryWrapper<LendItemReturn> wrapper = new LambdaQueryWrapper<LendItemReturn>()
                .eq(LendItemReturn::getLendReturnId, lendReturnId)
                .orderByAsc(LendItemReturn::getInvestUserId);
        // 归属校验（过滤下推 SQL，不把全量捞回内存再筛）：
        // 借款人是本期还款义务人，可看该期全部投资人明细；投资人只能看自己那条；
        // 与本期无关的用户条件恒不成立 → 返回空列表，不泄露任何数据。
        boolean isBorrower = userId != null && userId.equals(lendReturn.getUserId());
        if (!isBorrower) {
            wrapper.eq(LendItemReturn::getInvestUserId, userId);
        }
        return lendItemReturnMapper.selectList(wrapper);
    }

    @Override
    public List<LendItemReturn> listMyItemReturns(Long investUserId) {
        return lendItemReturnMapper.selectList(new LambdaQueryWrapper<LendItemReturn>()
                .eq(LendItemReturn::getInvestUserId, investUserId)
                .orderByDesc(LendItemReturn::getCurrentPeriod));
    }

    @Override
    public int overdueCheck() {
        LocalDate today = LocalDate.now();
        // 未还(0)或已逾期(2)且已过还款日的记录
        List<LendReturn> overdueList = this.list(new LambdaQueryWrapper<LendReturn>()
                .in(LendReturn::getStatus, 0, 2)
                .lt(LendReturn::getReturnDate, today));

        int count = 0;
        for (LendReturn lr : overdueList) {
            long overdueDays = ChronoUnit.DAYS.between(lr.getReturnDate(), today);
            if (overdueDays <= 0) {
                continue;
            }

            // 罚息 = 逾期本金 × 罚息日利率 × 逾期天数
            // 罚息日利率 = 年化利率/100/365 × 1.5
            BigDecimal penaltyDailyRate = lr.getLendYearRate()
                    .divide(HUNDRED, 10, RoundingMode.HALF_UP)
                    .divide(DAYS_OF_YEAR, 10, RoundingMode.HALF_UP)
                    .multiply(PENALTY_MULTIPLIER);
            BigDecimal penalty = lr.getPrincipal()
                    .multiply(penaltyDailyRate)
                    .multiply(new BigDecimal(overdueDays))
                    .setScale(2, RoundingMode.HALF_UP);
            // 罚息上限：不超过逾期本金
            BigDecimal penaltyCap = lr.getPrincipal().multiply(PENALTY_CAP_RATIO);
            if (penalty.compareTo(penaltyCap) > 0) {
                log.warn("罚息超过上限截断，原={}, 截断={}", penalty, penaltyCap);
                penalty = penaltyCap;
            }

            boolean firstOverdue = !Boolean.TRUE.equals(lr.getOverdue());

            lr.setOverdue(true);
            lr.setStatus(2);
            lr.setOverdueTotal(lr.getTotal().add(penalty));
            lr.setUpdateTime(LocalDateTime.now());
            this.updateById(lr);

            // 标的置为已逾期
            Lend lend = lendService.getById(lr.getLendId());
            if (lend != null && !LendStatusEnum.OVERDUE.getCode().equals(lend.getStatus())) {
                lend.setStatus(LendStatusEnum.OVERDUE.getCode());
                lend.setUpdateTime(LocalDateTime.now());
                lendService.updateById(lend);
            }

            // 首次逾期：发送催收通知（站内信 + 短信，与验证码同通道，失败容错）
            if (firstOverdue) {
                String content = String.format(
                        "您的借款第%d期已于%s到期，应还金额%s元，当前已逾期%d天，"
                                + "产生罚息%s元，应还总额%s元，请尽快还款以免影响您的信用记录。",
                        lr.getCurrentPeriod(),
                        lr.getReturnDate().toString(),
                        lr.getTotal().toPlainString(),
                        overdueDays,
                        penalty.toPlainString(),
                        lr.getOverdueTotal().toPlainString());
                messageService.sendOverdueNotice(lr.getUserId(), content);
                log.warn("逾期催收通知已发送：lendReturnId={}, userId={}, 逾期{}天, 罚息={}",
                        lr.getId(), lr.getUserId(), overdueDays, penalty);
                // 首次逾期扣减 50 积分，影响信用等级与可借额度
                try {
                    userIntegralService.subtractIntegral(lr.getUserId(), 50, "逾期违约扣分");
                } catch (Exception ex) {
                    log.error("逾期扣分失败，lendReturnId={}, userId={}", lr.getId(), lr.getUserId(), ex);
                }
            }
            count++;
        }
        log.info("逾期扫描完成，共处理 {} 条逾期记录", count);
        return count;
    }

    /**
     * 记录交易流水（使用银行流水号做幂等）
     */
    private void recordFlow(Long userId, TransTypeEnum type, BigDecimal amount, String memo, LocalDateTime now, String bankTransNo) {
        User user = userService.getById(userId);
        String userName = user != null ? (user.getName() != null ? user.getName() : user.getMobile()) : "";
        TransFlow flow = new TransFlow();
        flow.setUserId(userId);
        flow.setUserName(userName);
        String transNo = (bankTransNo != null && !bankTransNo.isEmpty())
                ? bankTransNo : LendNoUtils.getTransNo();

        // 【P0-1】若发起时已按同一单号落过「处理中」在途流水（还款/投标占位），
        // 就地把那条置为成功，避免同一笔业务留下两条流水。
        // 放款/投资回款没有占位流水，settlePending 是空操作，仍走下面的新建分支。
        transFlowService.settlePending(transNo, true);
        if (transFlowService.existsByTransNo(transNo)) {
            userAccountService.updateBalance(userId, type, amount);
            return;
        }

        flow.setTransNo(transNo);
        flow.setTransType(type.getCode());
        flow.setTransTypeName(type.getDesc());
        flow.setTransAmount(amount);
        flow.setMemo(memo);
        flow.setCreateTime(now);
        flow.setUpdateTime(now);
        transFlowService.save(flow);

        userAccountService.updateBalance(userId, type, amount);
    }
}