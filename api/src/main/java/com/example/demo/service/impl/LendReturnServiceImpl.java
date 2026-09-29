package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.Bank.FormHelper;
import com.example.demo.Bank.HfbConst;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.entity.LendItemReturn;
import com.example.demo.entity.LendReturn;
import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.enums.BankNotifyStatusEnum;
import com.example.demo.enums.BindStatusEnum;
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

    @Override
    public String buildMakeLoanForm(Long lendId) {
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
        paramMap.put("returnUrl", HfbConst.MAKE_LOAD_RETURN_URL);
        paramMap.put("notifyUrl", HfbConst.MAKE_LOAD_NOTIFY_URL);
        paramMap.put("timestamp", new Date().getTime());
        paramMap.put("sign", BankSignUtil.getSign(paramMap));

        log.info("放款表单生成，lendId={}, borrowerId={}, amount={}", lendId, lend.getUserId(), lend.getAmount());
        return FormHelper.buildForm(HfbConst.MAKE_LOAD_URL, paramMap);
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

        if ("0001".equals(status)) {
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

        LocalDateTime now = LocalDateTime.now();

        // 7. 二次校验标的（回调时状态可能已变）
        Lend lend = lendService.getById(lendId);
        if (lend == null) {
            log.warn("放款回调标的不存在，lendId={}", lendId);
            return false;
        }
        if (!LendStatusEnum.COMPLETED.getCode().equals(lend.getStatus())) {
            log.warn("放款回调标的不在满标待放款状态，lendId={}, status={}", lendId, lend.getStatus());
            return true;
        }

        // 8. 幂等：已有还款计划则不重复放款
        long exists = this.count(new LambdaQueryWrapper<LendReturn>()
                .eq(LendReturn::getLendId, lendId));
        if (exists > 0) {
            log.info("放款回调标的已生成还款计划，跳过重复放款，lendId={}", lendId);
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

        Map<Integer, Long> periodReturnIdMap = new HashMap<>();
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
        for (LendReturn lr : returns) {
            periodReturnIdMap.put(lr.getCurrentPeriod(), lr.getId());
        }

        // 10. 为每个投资人独立生成收益计划
        List<LendItem> items = lendItemService.list(new LambdaQueryWrapper<LendItem>()
                .eq(LendItem::getLendId, lendId));
        List<LendItemReturn> itemReturns = new ArrayList<>();
        for (LendItem item : items) {
            List<PeriodPlan> investorPlans = ReturnPlanCalculator.calc(
                    item.getInvestAmount(),
                    item.getLendYearRate(),
                    lend.getPeriod(),
                    lend.getReturnMethod(),
                    loanDate);
            for (PeriodPlan p : investorPlans) {
                LendItemReturn lir = new LendItemReturn();
                lir.setLendReturnId(periodReturnIdMap.get(p.getPeriod()));
                lir.setLendItemId(item.getId());
                lir.setLendId(lendId);
                lir.setInvestUserId(item.getInvestUserId());
                lir.setInvestAmount(item.getInvestAmount());
                lir.setCurrentPeriod(p.getPeriod());
                lir.setLendYearRate(item.getLendYearRate());
                lir.setReturnMethod(lend.getReturnMethod());
                lir.setPrincipal(p.getPrincipal());
                lir.setInterest(p.getInterest());
                lir.setTotal(p.getTotal());
                lir.setFee(BigDecimal.ZERO);
                lir.setReturnDate(p.getReturnDate());
                lir.setOverdue(false);
                lir.setStatus(0);
                lir.setCreateTime(now);
                lir.setUpdateTime(now);
                itemReturns.add(lir);
            }
        }
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

        log.info("标的 {} 放款回调处理成功，借款计划 {} 期，投资人收益明细 {} 条，transNo={}",
                lendId, returns.size(), itemReturns.size(), transNo);

        // 满标放款通知（给借款人，提示资金已入账+按时还款）
        messageService.sendMakeLoanNotice(lend.getUserId(), lend.getLendNo(), lend.getAmount());
        return true;
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
                : lendReturn.getTotal();

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
            detail.put("transitAmt", ir.getTotal());
            detail.put("baseAmt", ir.getPrincipal());
            detail.put("benifitAmt", ir.getInterest());
            detailList.add(detail);
        }

        // 6. 组装旺旺银行还款参数并签名
        //    银行 returnCommit 读取字段（字节码确认）：fromBindCode, totalAmt, voteFeeAmt, data
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", HfbConst.AGENT_ID);
        paramMap.put("agentUserId", userBind.getMobile());
        paramMap.put("fromBindCode", userBind.getBindCode());
        paramMap.put("totalAmt", payable);
        paramMap.put("voteFeeAmt", lendReturn.getFee() != null ? lendReturn.getFee() : BigDecimal.ZERO);
        paramMap.put("agentProjectCode", lendReturn.getLendId());
        paramMap.put("agentBatchNo", lendReturnId);
        paramMap.put("agentGoodsName", lend.getTitle());
        paramMap.put("data", com.alibaba.fastjson.JSON.toJSONString(detailList));
        paramMap.put("returnUrl", HfbConst.BORROW_RETURN_RETURN_URL);
        paramMap.put("notifyUrl", HfbConst.BORROW_RETURN_NOTIFY_URL);
        paramMap.put("timestamp", new Date().getTime());
        paramMap.put("sign", BankSignUtil.getSign(paramMap));

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
        if (!BankNotifyStatusEnum.isSuccess(status)) {
            log.info("还款回调银行状态非成功，status={}", status);
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
        recordFlow(borrower.getId(), TransTypeEnum.REPAY, lendReturn.getTotal(),
                "归还借款第" + lendReturn.getCurrentPeriod() + "期 " + lendReturn.getReturnNo(), now, transNo);
        // 逾期罚息单独记一条流水（罚息部分归平台，不参与投资人本息分摊）
        if (overdue) {
            BigDecimal penalty = payable.subtract(lendReturn.getTotal());
            if (penalty.compareTo(BigDecimal.ZERO) > 0) {
                recordFlow(borrower.getId(), TransTypeEnum.REPAY, penalty,
                        "第" + lendReturn.getCurrentPeriod() + "期逾期罚息", now, transNo + "_PENALTY");
            }
        }
        log.info("还款流水已记录，银行将扣减借款人余额 {}", borrower.getId());

        // 10. 查询本期所有投资人收益明细并逐个回款
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
        Lend lend = lendService.getById(lendReturn.getLendId());
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
        if (unpaid == 0) {
            try {
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
            } catch (Exception ex) {
                log.error("标的结清处理失败，lendId={}, borrowerId={}",
                        lend.getId(), borrower.getId(), ex);
            }
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
    public List<LendItemReturn> listItemReturns(Long lendReturnId) {
        return lendItemReturnMapper.selectList(new LambdaQueryWrapper<LendItemReturn>()
                .eq(LendItemReturn::getLendReturnId, lendReturnId)
                .orderByAsc(LendItemReturn::getInvestUserId));
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