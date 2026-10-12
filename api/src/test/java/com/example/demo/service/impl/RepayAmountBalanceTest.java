package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.entity.LendItemReturn;
import com.example.demo.entity.LendReturn;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.enums.BindStatusEnum;
import com.example.demo.mapper.LendItemReturnMapper;
import com.example.demo.mapper.LendReturnMapper;
import com.example.demo.service.BankAccountSyncService;
import com.example.demo.service.LendItemService;
import com.example.demo.service.LendService;
import com.example.demo.service.TransFlowService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.wwfinance.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 还款金额「银行强等式」保障（2026-10-08 修 P4-53 / P0-4）。
 *
 * <p><b>背景：</b>银行 {@code returnCommit} 校验
 * {@code voteFeeAmt + ΣtransitAmt == totalAmt}（转 double 比较），不等就直接抛
 * {@code RETURN_AMMOUNT_MORE_ERROR}，表现为"还款 100% 失败"。
 * 两个触发源：
 * <ol>
 *   <li><b>P4-53</b>：多投资人分摊各自四舍五入 ⇒ Σ明细 比 期供 多/少 1 分。
 *       实测标的10：86.99 + 782.90 = 869.89，而期供 869.88。</li>
 *   <li><b>P0-4</b>：逾期时 totalAmt = 本息 + 罚息，而明细只摊本息、voteFeeAmt 恒为 0。</li>
 * </ol>
 *
 * <p>本类把两条兜底钉住：
 * <ul>
 *   <li>存量脏数据（Σ明细 ≠ 期供）在组装表单时被倒挤到最后一户 ⇒ 等式成立；</li>
 *   <li>逾期时罚息计入 voteFeeAmt ⇒ 等式成立。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RepayAmountBalanceTest {

    @Mock
    private LendReturnMapper baseMapper;
    @Mock
    private LendService lendService;
    @Mock
    private LendItemService lendItemService;
    @Mock
    private LendItemReturnMapper lendItemReturnMapper;
    @Mock
    private BankAccountSyncService bankAccountSyncService;
    @Mock
    private UserService userService;
    @Mock
    private TransFlowService transFlowService;
    @Mock
    private UserBindService userBindService;

    @InjectMocks
    private LendReturnServiceImpl service;

    private static final Long USER_ID = 33L;
    private static final Long LEND_ID = 10L;
    private static final Long RETURN_ID = 100L;

    /** 从 FormHelper 生成的自动提交表单里取一个字段（表单用单引号包值） */
    private String field(String html, String name) {
        Matcher m = Pattern.compile("name='" + Pattern.quote(name) + "' value='([^']*)'").matcher(html);
        if (!m.find()) {
            throw new IllegalStateException("表单里没有字段 " + name);
        }
        return m.group(1);
    }

    private BigDecimal sumTransit(String dataJson) {
        // data 是 [{"transitAmt":86.99,...},...]
        BigDecimal sum = BigDecimal.ZERO;
        Matcher m = Pattern.compile("\"transitAmt\":\\s*([0-9.]+)").matcher(dataJson);
        while (m.find()) {
            sum = sum.add(new BigDecimal(m.group(1)));
        }
        return sum;
    }

    private LendReturn lendReturn(BigDecimal principal, BigDecimal interest, BigDecimal total,
                                  boolean overdue, BigDecimal overdueTotal) {
        LendReturn lr = new LendReturn();
        lr.setId(RETURN_ID);
        lr.setLendId(LEND_ID);
        lr.setUserId(USER_ID);
        lr.setCurrentPeriod(1);
        lr.setPrincipal(principal);
        lr.setInterest(interest);
        lr.setTotal(total);
        lr.setFee(BigDecimal.ZERO);
        lr.setStatus(0);
        lr.setOverdue(overdue);
        lr.setOverdueTotal(overdueTotal);
        return lr;
    }

    private LendItemReturn itemReturn(Long id, Long investUserId, BigDecimal principal,
                                      BigDecimal interest, BigDecimal total) {
        LendItemReturn ir = new LendItemReturn();
        ir.setId(id);
        ir.setLendReturnId(RETURN_ID);
        ir.setLendItemId(id);
        ir.setInvestUserId(investUserId);
        ir.setInvestAmount(new BigDecimal("5000.00"));
        ir.setPrincipal(principal);
        ir.setInterest(interest);
        ir.setTotal(total);
        ir.setStatus(0);
        return ir;
    }

    @SuppressWarnings("unchecked")
    private void stubCommon(LendReturn lr, List<LendItemReturn> itemReturns, BigDecimal available) {
        when(baseMapper.selectById(RETURN_ID)).thenReturn(lr);

        User borrower = new User();
        borrower.setId(USER_ID);
        borrower.setMobile("15138019132");
        when(userService.getById(USER_ID)).thenReturn(borrower);

        UserBind bind = new UserBind();
        bind.setUserId(USER_ID);
        bind.setBindCode("borrower-bind");
        bind.setStatus(BindStatusEnum.BOUND.getCode());
        when(userBindService.getByUserId(USER_ID)).thenReturn(bind);

        Lend lend = new Lend();
        lend.setId(LEND_ID);
        lend.setTitle("测试标的");
        lend.setLendNo("LEND_TEST_10");
        when(lendService.getById(LEND_ID)).thenReturn(lend);

        when(bankAccountSyncService.getAvailableBalance(USER_ID)).thenReturn(available);
        when(lendItemReturnMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(itemReturns);

        for (LendItemReturn ir : itemReturns) {
            UserBind ib = new UserBind();
            ib.setUserId(ir.getInvestUserId());
            ib.setBindCode("investor-bind-" + ir.getInvestUserId());
            when(userBindService.getByUserId(ir.getInvestUserId())).thenReturn(ib);
            LendItem li = new LendItem();
            li.setId(ir.getLendItemId());
            li.setLendItemNo("INVEST-NO-" + ir.getLendItemId());
            when(lendItemService.getById(ir.getLendItemId())).thenReturn(li);
        }
        // savePending 是 void 方法（占位流水），无需 stub 返回值
    }

    @Test
    @DisplayName("【P4-53】多投资人分厘差：Σ明细比期供多1分时，倒挤到最后一户使等式成立")
    void multiInvestorPennyGap_isBalancedIntoLastInvestor() {
        // 期供 869.88，但两个投资人明细分别四舍五入后合计 869.89（+0.01）
        LendReturn lr = lendReturn(new BigDecimal("803.22"), new BigDecimal("66.67"),
                new BigDecimal("869.88"), false, null);
        List<LendItemReturn> items = new ArrayList<>(Arrays.asList(
                itemReturn(1L, 42L, new BigDecimal("80.32"), new BigDecimal("6.67"), new BigDecimal("86.99")),
                itemReturn(2L, 22L, new BigDecimal("722.90"), new BigDecimal("60.00"), new BigDecimal("782.90"))));
        stubCommon(lr, items, new BigDecimal("20000.00"));

        String html = service.buildRepayForm(RETURN_ID, USER_ID);

        BigDecimal totalAmt = new BigDecimal(field(html, "totalAmt"));
        BigDecimal voteFeeAmt = new BigDecimal(field(html, "voteFeeAmt"));
        BigDecimal sum = sumTransit(field(html, "data"));

        assertEquals(0, voteFeeAmt.add(sum).compareTo(totalAmt),
                "银行强等式 voteFeeAmt + ΣtransitAmt == totalAmt 必须成立，否则还款必被拒；"
                        + " totalAmt=" + totalAmt + " voteFeeAmt=" + voteFeeAmt + " ΣtransitAmt=" + sum);
        assertEquals(0, sum.compareTo(new BigDecimal("869.88")),
                "倒挤后 ΣtransitAmt 应等于期供 869.88（第二户从 782.90 调整为 782.89）");
    }

    @Test
    @DisplayName("【P4-53】明细本来就自洽时不应被改动")
    void alreadyBalanced_isNotTouched() {
        LendReturn lr = lendReturn(new BigDecimal("803.22"), new BigDecimal("66.67"),
                new BigDecimal("869.89"), false, null);
        List<LendItemReturn> items = new ArrayList<>(Arrays.asList(
                itemReturn(1L, 42L, new BigDecimal("80.32"), new BigDecimal("6.67"), new BigDecimal("86.99")),
                itemReturn(2L, 22L, new BigDecimal("722.90"), new BigDecimal("60.00"), new BigDecimal("782.90"))));
        stubCommon(lr, items, new BigDecimal("20000.00"));

        String html = service.buildRepayForm(RETURN_ID, USER_ID);
        BigDecimal sum = sumTransit(field(html, "data"));
        assertEquals(0, sum.compareTo(new BigDecimal("869.89")),
                "自洽的明细不应被倒挤改动，Σ 应仍是 869.89");
    }

    @Test
    @DisplayName("【P0-4】逾期：罚息计入 voteFeeAmt，使等式成立")
    void overduePenalty_goesIntoVoteFeeAmt() {
        // 期供本息 869.89，逾期应扣 879.89，罚息 10.00
        LendReturn lr = lendReturn(new BigDecimal("803.22"), new BigDecimal("66.67"),
                new BigDecimal("869.89"), true, new BigDecimal("879.89"));
        List<LendItemReturn> items = new ArrayList<>(Arrays.asList(
                itemReturn(1L, 42L, new BigDecimal("803.22"), new BigDecimal("66.67"), new BigDecimal("869.89"))));
        stubCommon(lr, items, new BigDecimal("20000.00"));

        String html = service.buildRepayForm(RETURN_ID, USER_ID);

        BigDecimal totalAmt = new BigDecimal(field(html, "totalAmt"));
        BigDecimal voteFeeAmt = new BigDecimal(field(html, "voteFeeAmt"));
        BigDecimal sum = sumTransit(field(html, "data"));

        assertEquals(0, totalAmt.compareTo(new BigDecimal("879.89")), "应扣金额应含罚息");
        assertEquals(0, voteFeeAmt.compareTo(new BigDecimal("10.00")), "罚息 10.00 应计进 voteFeeAmt");
        assertEquals(0, voteFeeAmt.add(sum).compareTo(totalAmt),
                "逾期时等式同样必须成立：10.00 + 869.89 == 879.89");
    }

    @Test
    @DisplayName("倒挤会让投资人回款变负时，直接拒绝而不是把脏单子递给银行")
    void negativeAdjustment_isRejected() {
        // 期供远小于明细合计，倒挤会把最后一户压成负数
        LendReturn lr = lendReturn(new BigDecimal("100.00"), new BigDecimal("10.00"),
                new BigDecimal("110.00"), false, null);
        List<LendItemReturn> items = new ArrayList<>(Arrays.asList(
                itemReturn(1L, 42L, new BigDecimal("5000.00"), new BigDecimal("10.00"), new BigDecimal("5010.00")),
                itemReturn(2L, 22L, new BigDecimal("20.00"), new BigDecimal("1.00"), new BigDecimal("21.00"))));
        stubCommon(lr, items, new BigDecimal("20000.00"));

        assertThrows(ResourceNotFoundException.class,
                () -> service.buildRepayForm(RETURN_ID, USER_ID),
                "倒挤后回款为负说明还款计划数据异常，必须拒绝并留痕");
    }
}
