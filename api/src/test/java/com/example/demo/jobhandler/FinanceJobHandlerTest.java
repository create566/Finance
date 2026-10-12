package com.example.demo.jobhandler;

import com.example.demo.entity.Contract;
import com.example.demo.entity.Lend;
import com.example.demo.enums.ContractBizTypeEnum;
import com.example.demo.enums.ContractStatusEnum;
import com.example.demo.enums.ContractTypeEnum;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.service.ContractService;
import com.example.demo.service.LendReturnService;
import com.example.demo.service.LendService;
import com.example.demo.service.ReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超期未签《放款确认书》自动退资定时任务的单元测试。
 *
 * <p>纯 Mockito，可离线跑。重点锁定"防误伤"：这个任务会动投资人的真金白银，
 * 一旦边界判错就会退掉本该正常放款的标的，所以三个跳过条件都必须有用例钉住。</p>
 *
 * <p>注意：起算点（{@code complete_time} 早于 N 天前）由 SQL 的 WHERE 条件过滤，
 * 本测试 mock 了 {@code lendService.list(...)}，因此只能覆盖"进入候选集之后"的分支。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("超期未签自动退资定时任务测试")
class FinanceJobHandlerTest {

    @Mock
    private LendReturnService lendReturnService;
    @Mock
    private ReconciliationService reconciliationService;
    @Mock
    private LendService lendService;
    @Mock
    private ContractService contractService;

    @InjectMocks
    private FinanceJobHandler jobHandler;

    private Lend completedLend(Long id) {
        Lend lend = new Lend();
        lend.setId(id);
        lend.setLendNo("LEND_JOB_" + id);
        lend.setStatus(LendStatusEnum.COMPLETED.getCode());
        // 起算点：10 天前满标，早已超过 3 天宽限
        lend.setCompleteTime(LocalDateTime.now().minusDays(10));
        return lend;
    }

    private Contract paymentContract(Integer status) {
        Contract c = new Contract();
        c.setId(900L);
        c.setBizType(ContractBizTypeEnum.LEND.getCode());
        c.setContractType(ContractTypeEnum.PAYMENT.getCode());
        c.setStatus(status);
        return c;
    }

    @Test
    @DisplayName("超期且《放款确认书》未签 → 触发系统自动退资")
    void refund_timeoutAndUnsigned_triggersSystemOffline() {
        Lend lend = completedLend(120L);
        when(lendService.list(any())).thenReturn(Collections.singletonList(lend));
        when(contractService.list(any())).thenReturn(
                Collections.singletonList(paymentContract(ContractStatusEnum.PENDING.getCode())));

        jobHandler.refundUnsignedCompletedLends();

        verify(lendService).offlineBySystem(120L);
    }

    @Test
    @DisplayName("《放款确认书》已签署 → 跳过（借款人已签、只是运营还没点放款，属于正常待放款）")
    void refund_paymentSigned_skips() {
        Lend lend = completedLend(121L);
        when(lendService.list(any())).thenReturn(Collections.singletonList(lend));
        when(contractService.list(any())).thenReturn(
                Collections.singletonList(paymentContract(ContractStatusEnum.SIGNED.getCode())));

        jobHandler.refundUnsignedCompletedLends();

        verify(lendService, never()).offlineBySystem(any());
    }

    @Test
    @DisplayName("已发起放款（paymentAdminId 非空）→ 跳过，避免资金账目错乱")
    void refund_alreadyDisbursing_skips() {
        Lend lend = completedLend(122L);
        lend.setPaymentAdminId(7L);
        when(lendService.list(any())).thenReturn(Collections.singletonList(lend));
        when(contractService.list(any())).thenReturn(
                Collections.singletonList(paymentContract(ContractStatusEnum.PENDING.getCode())));

        jobHandler.refundUnsignedCompletedLends();

        verify(lendService, never()).offlineBySystem(any());
    }

    @Test
    @DisplayName("合同记录不存在时按「未签」处理 → 触发退资（没签过确认书本就该退）")
    void refund_noContractRecord_treatsAsUnsigned() {
        Lend lend = completedLend(123L);
        when(lendService.list(any())).thenReturn(Collections.singletonList(lend));
        when(contractService.list(any())).thenReturn(Collections.emptyList());

        jobHandler.refundUnsignedCompletedLends();

        verify(lendService).offlineBySystem(123L);
    }

    @Test
    @DisplayName("单笔处置抛异常不影响同一批里的其他标的")
    void refund_oneFails_othersStillProcessed() {
        Lend bad = completedLend(124L);
        Lend good = completedLend(125L);
        when(lendService.list(any())).thenReturn(List




                .of(bad, good));
        when(contractService.list(any())).thenReturn(
                Collections.singletonList(paymentContract(ContractStatusEnum.PENDING.getCode())));
        when(lendService.offlineBySystem(124L)).thenThrow(new RuntimeException("银行侧解冻失败"));

        jobHandler.refundUnsignedCompletedLends();

        verify(lendService).offlineBySystem(124L);
        verify(lendService).offlineBySystem(125L);
    }
}
