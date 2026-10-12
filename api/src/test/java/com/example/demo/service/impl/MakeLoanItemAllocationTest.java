package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.entity.LendItemReturn;
import com.example.demo.entity.LendReturn;
import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.mapper.LendItemReturnMapper;
import com.example.demo.mapper.LendReturnMapper;
import com.example.demo.service.BankAccountSyncService;
import com.example.demo.service.ContractService;
import com.example.demo.service.LendItemService;
import com.example.demo.service.LendService;
import com.example.demo.service.LendSettleRecordService;
import com.example.demo.service.MessageService;
import com.example.demo.service.TransFlowService;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserIntegralService;
import com.example.demo.service.UserService;
import com.example.demo.utils.ReturnPlanCalculator;
import com.example.demo.utils.ReturnPlanCalculator.PeriodPlan;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.wwfinance.common.utils.BankSignUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 放款分摊的「恒等式③」保障：<b>每期 Σ各户明细 ≡ 该期期供</b>。
 *
 * <p><b>为什么必须成立：</b>银行 {@code returnCommit} 的强等式是
 * {@code voteFeeAmt + ΣtransitAmt == totalAmt}（转 double 比较）。平台按
 * {@code lend_item_return.total} 逐户打款，只要 Σ明细 与期供差 1 分，整期还款 100% 被拒。
 *
 * <p><b>本类钉住三件事：</b>
 * <ol>
 *   <li>每期 Σ本金 / Σ利息 / Σ总额 与期供**分位严格相等**（不是"近似相等"）；</li>
 *   <li>每户每期一行，户数 × 期数 不多不少；</li>
 *   <li>单户 <b>total ≡ principal + interest</b>（不独立舍入），避免三处口径互不一致。</li>
 * </ol>
 *
 * <p>算法本身（最大余数法）的数学性质由 {@code FundInvariantTest} 覆盖；
 * 本类只负责证明<b>服务层真的把算法接上了</b>——历史教训是"工具写对了但没接线"。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("放款分摊恒等式测试（恒等式③：Σ明细 ≡ 期供）")
class MakeLoanItemAllocationTest {

    @Mock
    private LendReturnMapper baseMapper;
    @Mock
    private LendService lendService;
    @Mock
    private LendItemService lendItemService;
    @Mock
    private LendItemReturnMapper lendItemReturnMapper;
    @Mock
    private UserAccountService userAccountService;
    @Mock
    private BankAccountSyncService bankAccountSyncService;
    @Mock
    private UserService userService;
    @Mock
    private TransFlowService transFlowService;
    @Mock
    private UserBindService userBindService;
    @Mock
    private MessageService messageService;
    @Mock
    private UserIntegralService userIntegralService;
    @Mock
    private LendSettleRecordService lendSettleRecordService;
    @Mock
    private ContractService contractService;

    /** 用 Spy 是为了拦掉 ServiceImpl 的 saveBatch（它需要真实 SqlSessionFactory，纯 Mockito 环境不可用） */
    @Spy
    @InjectMocks
    private LendReturnServiceImpl service;

    private static final Long LEND_ID = 106L;
    private static final String BIND_CODE = "bind-code-001";
    private static final String MOBILE = "13800001111";

    private static final BigDecimal LEND_AMOUNT = new BigDecimal("60000.00");
    private static final BigDecimal YEAR_RATE = new BigDecimal("12.00");
    private static final int PERIOD = 6;
    private static final int RETURN_METHOD = 1;

    private Map<String, Object> notifyParams() {
        return notifyParams(LEND_AMOUNT.toPlainString());
    }

    private Map<String, Object> notifyParams(String voteAmt) {
        Map<String, Object> p = new HashMap<>();
        p.put("agentUserId", MOBILE);
        p.put("bindCode", BIND_CODE);
        p.put("voteAmt", voteAmt);
        p.put("agentBillNo", "MAKELoan20261012160000106");
        p.put("agentProjectCode", String.valueOf(LEND_ID));
        p.put("resultCode", "0000");
        p.put("sign", BankSignUtil.getSign(p));
        return p;
    }

    private Lend completedLend() {
        Lend lend = new Lend();
        lend.setId(LEND_ID);
        lend.setUserId(35L);
        lend.setStatus(LendStatusEnum.COMPLETED.getCode());
        lend.setAmount(LEND_AMOUNT);
        lend.setInvestAmount(LEND_AMOUNT);
        lend.setPeriod(PERIOD);
        lend.setLendYearRate(YEAR_RATE);
        lend.setReturnMethod(RETURN_METHOD);
        lend.setLendNo("LEND_TEST_106");
        return lend;
    }

    private LendItem item(Long id, Long investorUserId, String amount) {
        LendItem li = new LendItem();
        li.setId(id);
        li.setLendId(LEND_ID);
        li.setInvestUserId(investorUserId);
        li.setInvestName("投资人" + investorUserId);
        li.setInvestAmount(new BigDecimal(amount));
        li.setLendYearRate(YEAR_RATE);
        li.setStatus(2);
        return li;
    }

    /** 收集落库的每户明细，按期聚合 */
    private Map<Integer, BigDecimal[]> sumByPeriod(List<LendItemReturn> all) {
        // value = [principal, interest, total, 户数]
        Map<Integer, BigDecimal[]> byPeriod = new HashMap<>();
        for (LendItemReturn ir : all) {
            BigDecimal[] agg = byPeriod.computeIfAbsent(ir.getCurrentPeriod(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            agg[0] = agg[0].add(ir.getPrincipal());
            agg[1] = agg[1].add(ir.getInterest());
            agg[2] = agg[2].add(ir.getTotal());
            agg[3] = agg[3].add(BigDecimal.ONE);

            // 单户内部自洽：total 必须由 principal + interest 相加得出，不独立舍入
            assertEquals(0, ir.getTotal().compareTo(ir.getPrincipal().add(ir.getInterest())),
                    "每户 total 必须 ≡ principal + interest");
        }
        return byPeriod;
    }

    @Test
    @DisplayName("3 户投资额 10000/20000/30000：每期 Σ本金/Σ利息/Σ总额 都与期供分位严格相等")
    void itemAllocation_eachPeriodSumsExactlyToPeriodDue() {
        User user = new User();
        user.setId(35L);
        when(userService.getByMobile(MOBILE)).thenReturn(user);
        when(transFlowService.existsByTransNo(any())).thenReturn(false);
        when(lendService.getById(LEND_ID)).thenReturn(completedLend());

        List<LendItem> items = new ArrayList<>(Arrays.asList(
                item(1L, 42L, "10000.00"),
                item(2L, 22L, "20000.00"),
                item(3L, 100L, "30000.00")));
        when(lendItemService.list(any(LambdaQueryWrapper.class))).thenReturn(items);

        // saveBatch 需要真实 SqlSessionFactory → 纯 Mockito 环境下直接放行
        doReturn(true).when(service).saveBatch(anyList());

        assertTrue(service.handleMakeLoanNotify(notifyParams()), "放款落账应成功");

        ArgumentCaptor<LendItemReturn> captor = ArgumentCaptor.forClass(LendItemReturn.class);
        verify(lendItemReturnMapper, times(PERIOD * items.size())).insert(captor.capture());

        Map<Integer, BigDecimal[]> byPeriod = sumByPeriod(captor.getAllValues());
        List<PeriodPlan> plans = ReturnPlanCalculator.calc(
                LEND_AMOUNT, YEAR_RATE, PERIOD, RETURN_METHOD, LocalDate.now());

        assertEquals(plans.size(), byPeriod.size(), "每一期都必须有明细，且不多出一期");
        for (PeriodPlan p : plans) {
            BigDecimal[] agg = byPeriod.get(p.getPeriod());
            String tip = "第" + p.getPeriod() + "期：";
            assertEquals(0, agg[3].compareTo(new BigDecimal(items.size())),
                    tip + "明细行数应等于户数（不多不少）");
            assertEquals(0, agg[0].compareTo(p.getPrincipal()),
                    tip + "Σ本金 必须严格等于期供本金（差 1 分银行就拒付）");
            assertEquals(0, agg[1].compareTo(p.getInterest()),
                    tip + "Σ利息 必须严格等于期供利息");
            assertEquals(0, agg[2].compareTo(p.getTotal()),
                    tip + "Σ总额 必须严格等于期供总额");
        }
    }

    @Test
    @DisplayName("投资份额合计 ≠ 标的金额：直接中止放款分摊，不把缺口摊薄给所有人")
    void weightSumMismatch_abortsAllocation() {
        User user = new User();
        user.setId(35L);
        when(userService.getByMobile(MOBILE)).thenReturn(user);
        when(transFlowService.existsByTransNo(any())).thenReturn(false);
        when(lendService.getById(LEND_ID)).thenReturn(completedLend());

        // 少了一份债权：9999 + 20000 + 30000 = 59999 ≠ 60000
        List<LendItem> items = new ArrayList<>(Arrays.asList(
                item(1L, 42L, "9999.00"),
                item(2L, 22L, "20000.00"),
                item(3L, 100L, "30000.00")));
        when(lendItemService.list(any(LambdaQueryWrapper.class))).thenReturn(items);

        doReturn(true).when(service).saveBatch(anyList());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.wwfinance.common.exception.ResourceNotFoundException.class,
                () -> service.handleMakeLoanNotify(notifyParams()),
                "Σ投资份额 ≠ 标的金额 时必须中止，否则缺口会被静默摊薄到所有投资人身上");

        verify(lendItemReturnMapper, times(0)).insert(any(LendItemReturn.class));
    }

    @Test
    @DisplayName("银行实到金额 ≠ 标的金额：中止落账，不生成金额错误的还款计划")
    void bankAmountMismatch_abortsBooking() {
        User user = new User();
        user.setId(35L);
        when(userService.getByMobile(MOBILE)).thenReturn(user);
        when(transFlowService.existsByTransNo(any())).thenReturn(false);
        when(lendService.getById(LEND_ID)).thenReturn(completedLend());

        // 银行只划了 59999，而标的是 60000
        assertThrows(ResourceNotFoundException.class,
                () -> service.handleMakeLoanNotify(notifyParams("59999.00")),
                "银行实到金额与标的金额不一致时必须中止，否则会生成虚高/缩水的债权");

        verify(lendItemReturnMapper, times(0)).insert(any(LendItemReturn.class));
        verify(transFlowService, times(0)).save(any(TransFlow.class));
    }

    @Test
    @DisplayName("放款前置闸门：Σ满标投资份额 ≠ 标的金额时，连银行都不请求")
    void shareMismatch_rejectedBeforeCallingBank() {
        when(lendService.getById(LEND_ID)).thenReturn(completedLend());
        // 份额少了 1 元：9999 + 20000 + 30000 = 59999
        when(lendItemService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>(Arrays.asList(
                        item(1L, 42L, "9999.00"),
                        item(2L, 22L, "20000.00"),
                        item(3L, 100L, "30000.00"))));

        assertThrows(ResourceNotFoundException.class,
                () -> service.buildMakeLoanForm(LEND_ID, 1L),
                "份额与标的金额不符时必须在请求银行之前拒绝");

        verify(lendService, times(0)).updateById(any(Lend.class)); // 连"放款发起留痕"都不写
    }
}
