package com.example.demo.service.impl;

import com.example.demo.entity.ReconciliationRecord;
import com.example.demo.entity.TransFlow;
import com.example.demo.mapper.ReconciliationRecordMapper;
import com.example.demo.mapper.TransFlowMapper;
import com.example.demo.service.BankAccountSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 银行对账单元测试。
 *
 * <p>背景：{@code reconcileWithBank} 声明了 {@code @Transactional(rollbackFor = Exception.class)}，
 * 但方法体整体包在 {@code try{...}catch(Exception e)} 中。异常被吞掉后事务不会回滚，
 * 已经写入的差异记录会被提交，调用方却只看到一句"对账异常"文案 —— 典型的静默部分提交。
 * 而且 {@code reconciliation_record} 的 trans_no / trans_type / user_id / amount 均为 NOT NULL，
 * 银行少回一个字段就会 NPE（同样被吞）。
 *
 * <p>纯 Mockito：不依赖 MySQL / Redis / 银行接口，可离线回归。锁定的不变量：
 * <ol>
 *   <li>底层异常必须向上抛出，绝不能退化成"返回一句文案"</li>
 *   <li>银行字段缺失不得 NPE，用占位值落库</li>
 *   <li>缺少 transNo 的银行流水不落库，单独计数</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("银行对账事务边界测试")
class ReconciliationServiceImplTest {

    private static final LocalDateTime BIZ_DATE = LocalDateTime.of(2026, 9, 30, 3, 30);
    private static final String TARGET_DATE = "2026-09-30";

    @Mock
    private ReconciliationRecordMapper baseMapper;

    @Mock
    private TransFlowMapper transFlowMapper;

    @Mock
    private BankAccountSyncService bankAccountSyncService;

    @InjectMocks
    private ReconciliationServiceImpl reconciliationService;

    private Map<String, Object> bankFlow(String transNo, Object type, Object userId, Object amount) {
        Map<String, Object> flow = new HashMap<>();
        flow.put("transNo", transNo);
        if (type != null) {
            flow.put("type", type);
        }
        if (userId != null) {
            flow.put("userId", userId);
        }
        if (amount != null) {
            flow.put("amount", amount);
        }
        return flow;
    }

    private TransFlow localFlow(String transNo, Integer type, Long userId, String amount) {
        TransFlow flow = new TransFlow();
        flow.setTransNo(transNo);
        flow.setTransType(type);
        flow.setUserId(userId);
        flow.setUserName("测试用户");
        flow.setTransAmount(new BigDecimal(amount));
        return flow;
    }

    @Test
    @DisplayName("银行接口异常必须向上抛出，不得被吞成一句'对账异常'文案")
    void bankApiFails_exceptionPropagates_nothingWritten() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE)))
                .thenThrow(new IllegalStateException("银行接口超时"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> reconciliationService.reconcileWithBank(BIZ_DATE));

        assertEquals("银行接口超时", ex.getMessage());
        // 关键：异常必须能触发事务回滚，因此不能有任何差异记录被写入
        verify(baseMapper, never()).insert(any(ReconciliationRecord.class));
    }

    @Test
    @DisplayName("银行流水缺少 type/userId/amount 时用占位值落库，不得 NPE（NOT NULL 列）")
    void bankFlowMissingOptionalFields_writesPlaceholderRecord() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE)))
                .thenReturn(Collections.singletonList(bankFlow("B-KEY-1", null, null, null)));
        when(transFlowMapper.selectList(any())).thenReturn(Collections.emptyList());

        String summary = reconciliationService.reconcileWithBank(BIZ_DATE);

        ArgumentCaptor<ReconciliationRecord> captor = ArgumentCaptor.forClass(ReconciliationRecord.class);
        verify(baseMapper, times(1)).insert(captor.capture());

        ReconciliationRecord record = captor.getValue();
        assertEquals("B-KEY-1", record.getTransNo());
        assertEquals(0, record.getTransType(), "未知交易类型应落占位值 0");
        assertEquals("未知类型", record.getTransTypeName());
        assertEquals(0L, record.getUserId(), "缺失 user_id 应落占位值 0");
        assertEquals(0, BigDecimal.ZERO.compareTo(record.getAmount()), "缺失金额应落 0.00");
        assertEquals(1, record.getStatus(), "status=1 表示本地缺失");
        assertNotNull(record.getCreateTime());
        assertTrue(summary.contains("缺失：1"), "summary 应统计缺失 1 笔，实际：" + summary);
    }

    @Test
    @DisplayName("银行流水缺少 transNo 时无法定位，只计数不落库")
    void bankFlowMissingTransNo_countedAsAbnormal_notPersisted() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE)))
                .thenReturn(Collections.singletonList(bankFlow(null, 1, 42L, "50.00")));
        when(transFlowMapper.selectList(any())).thenReturn(Collections.emptyList());

        String summary = reconciliationService.reconcileWithBank(BIZ_DATE);

        verify(baseMapper, never()).insert(any(ReconciliationRecord.class));
        assertTrue(summary.contains("无法比对：1"), "summary 应统计无法比对 1 笔，实际：" + summary);
    }

    @Test
    @DisplayName("金额与单号全部匹配 → 不产生任何差异记录")
    void allMatched_noDiffRecords() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE)))
                .thenReturn(Collections.singletonList(bankFlow("T1", 3, 42L, "100.00")));
        when(transFlowMapper.selectList(any()))
                .thenReturn(Collections.singletonList(localFlow("T1", 3, 42L, "100.00")));

        String summary = reconciliationService.reconcileWithBank(BIZ_DATE);

        verify(baseMapper, never()).insert(any(ReconciliationRecord.class));
        assertTrue(summary.contains("匹配：1"), "summary 应统计匹配 1 笔，实际：" + summary);
        assertTrue(summary.contains("不一致：0") && summary.contains("缺失：0"), "不应有差异，实际：" + summary);
    }

    @Test
    @DisplayName("金额不一致 → 落 status=2 的差异记录，并带双方金额")
    void amountMismatch_writesRecordWithStatus2() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE)))
                .thenReturn(Collections.singletonList(bankFlow("T2", 3, 42L, "100.00")));
        when(transFlowMapper.selectList(any()))
                .thenReturn(Collections.singletonList(localFlow("T2", 3, 42L, "88.00")));

        reconciliationService.reconcileWithBank(BIZ_DATE);

        ArgumentCaptor<ReconciliationRecord> captor = ArgumentCaptor.forClass(ReconciliationRecord.class);
        verify(baseMapper, times(1)).insert(captor.capture());

        ReconciliationRecord record = captor.getValue();
        assertEquals(2, record.getStatus(), "status=2 表示金额不一致");
        assertEquals("投标", record.getTransTypeName(), "trans_type=3 应解析为投标");
        assertTrue(record.getRemark().contains("银行=100.00") && record.getRemark().contains("本地=88.00"),
                "备注应写明双方金额，实际：" + record.getRemark());
    }

    @Test
    @DisplayName("本地有、银行无 → 落 status=1 且 bankTransNo=N/A")
    void localOnly_writesRecordWithNaBankNo() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE)))
                .thenReturn(Collections.singletonList(bankFlow("B1", 1, 42L, "10.00")));
        when(transFlowMapper.selectList(any()))
                .thenReturn(Collections.singletonList(localFlow("L1", 4, 42L, "20.00")));

        reconciliationService.reconcileWithBank(BIZ_DATE);

        ArgumentCaptor<ReconciliationRecord> captor = ArgumentCaptor.forClass(ReconciliationRecord.class);
        // 两笔差异：B1 银行多出、L1 本地多出
        verify(baseMapper, times(2)).insert(captor.capture());

        List<ReconciliationRecord> records = captor.getAllValues();
        ReconciliationRecord localOnly = records.stream()
                .filter(r -> "L1".equals(r.getTransNo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应存在本地多出的差异记录 L1"));
        assertEquals("N/A", localOnly.getBankTransNo());
        assertEquals(1, localOnly.getStatus());

        ReconciliationRecord bankOnly = records.stream()
                .filter(r -> "B1".equals(r.getTransNo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应存在银行多出的差异记录 B1"));
        assertEquals("B1", bankOnly.getBankTransNo());
        assertEquals(1, bankOnly.getStatus());
    }

    @Test
    @DisplayName("银行流水为空 → 直接返回异常文案，不查本地也不落库")
    void emptyBankFlows_returnsAbnormalMessage() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE))).thenReturn(Collections.emptyList());

        String summary = reconciliationService.reconcileWithBank(BIZ_DATE);

        assertTrue(summary.contains("银行流水为空"), "实际：" + summary);
        verify(transFlowMapper, never()).selectList(any());
        verify(baseMapper, never()).insert(any(ReconciliationRecord.class));
    }

    @Test
    @DisplayName("本地流水缺少 transNo 时跳过，不影响其他流水比对")
    void localFlowMissingTransNo_skipped() {
        TransFlow broken = new TransFlow();
        broken.setTransType(3);
        broken.setUserId(42L);
        broken.setTransAmount(new BigDecimal("10.00"));

        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE)))
                .thenReturn(Collections.singletonList(bankFlow("T3", 3, 42L, "10.00")));
        when(transFlowMapper.selectList(any())).thenReturn(Arrays.asList(broken, localFlow("T3", 3, 42L, "10.00")));

        String summary = reconciliationService.reconcileWithBank(BIZ_DATE);

        verify(baseMapper, never()).insert(any(ReconciliationRecord.class));
        assertTrue(summary.contains("匹配：1"), "实际：" + summary);
    }
}
