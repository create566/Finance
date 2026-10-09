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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
 * <p><b>P1-9（2026-10-08 修复）</b>：原先 {@code reconciliation_record} <b>只在有差异时</b>落库，
 * 于是"表里 0 行"既可能是"没差异"也可能是"压根没跑"，两者不可辨。
 * 现在每次对账都写一条 {@code trans_no = RECON-HEARTBEAT-<日期>} 的<b>心跳记录</b>（一天一行）。
 * ⚠️ 由此本测试类原来的若干 {@code verify(never()).insert(...)} 断言<b>本身就固化了这个 bug</b>
 * ——"全部匹配 → 不落库"恰恰是的问题。现已统一改为断言「<b>差异</b>记录为空，但心跳记录必须存在」。
 *
 * <p>纯 Mockito：不依赖 MySQL / Redis / 银行接口，可离线回归。锁定的不变量：
 * <ol>
 *   <li>底层异常必须向上抛出，绝不能退化成"返回一句文案"</li>
 *   <li>银行字段缺失不得 NPE，用占位值落库</li>
 *   <li>缺少 transNo 的银行流水不落库，单独计数</li>
 *   <li>无论有无差异，每次对账都必须落一条心跳记录（P1-9）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("银行对账事务边界测试")
class ReconciliationServiceImplTest {

    private static final LocalDateTime BIZ_DATE = LocalDateTime.of(2026, 9, 30, 3, 30);
    private static final String TARGET_DATE = "2026-09-30";

    /** 心跳记录的单号，必须与 {@code ReconciliationServiceImpl#HEARTBEAT_PREFIX} 保持一致。 */
    private static final String HEARTBEAT_NO = "RECON-HEARTBEAT-" + TARGET_DATE;

    @Mock
    private ReconciliationRecordMapper baseMapper;

    @Mock
    private TransFlowMapper transFlowMapper;

    @Mock
    private BankAccountSyncService bankAccountSyncService;

    @InjectMocks
    private ReconciliationServiceImpl reconciliationService;

    /**
     * 捕获本次对账写入的记录，并按是否为心跳行分堆。
     */
    private List<ReconciliationRecord> capturedInserts() {
        ArgumentCaptor<ReconciliationRecord> captor = ArgumentCaptor.forClass(ReconciliationRecord.class);
        verify(baseMapper, atLeastOnce()).insert(captor.capture());
        return captor.getAllValues();
    }

    /** 差异记录（剔除心跳行）。 */
    private List<ReconciliationRecord> diffRecords() {
        return capturedInserts().stream()
                .filter(r -> !HEARTBEAT_NO.equals(r.getTransNo()))
                .collect(Collectors.toList());
    }

    /** 心跳记录；不存在即断言失败。 */
    private ReconciliationRecord heartbeatRecord() {
        return capturedInserts().stream()
                .filter(r -> HEARTBEAT_NO.equals(r.getTransNo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应存在对账心跳记录：" + HEARTBEAT_NO));
    }

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
        // 关键：异常必须能触发事务回滚，因此不能有任何记录被写入（含心跳）
        verify(baseMapper, never()).insert(any(ReconciliationRecord.class));
    }

    @Test
    @DisplayName("银行流水缺少 type/userId/amount 时用占位值落库，不得 NPE（NOT NULL 列）")
    void bankFlowMissingOptionalFields_writesPlaceholderRecord() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE)))
                .thenReturn(Collections.singletonList(bankFlow("B-KEY-1", null, null, null)));
        when(transFlowMapper.selectList(any())).thenReturn(Collections.emptyList());

        String summary = reconciliationService.reconcileWithBank(BIZ_DATE);

        List<ReconciliationRecord> diffs = diffRecords();
        assertEquals(1, diffs.size(), "应只有 1 条差异记录（另 1 条为心跳），实际：" + diffs.size());

        ReconciliationRecord record = diffs.get(0);
        assertEquals("B-KEY-1", record.getTransNo());
        assertEquals(0, record.getTransType(), "未知交易类型应落占位值 0");
        assertEquals("未知类型", record.getTransTypeName());
        assertEquals(0L, record.getUserId(), "缺失 user_id 应落占位值 0");
        assertEquals(0, BigDecimal.ZERO.compareTo(record.getAmount()), "缺失金额应落 0.00");
        assertEquals(1, record.getStatus(), "status=1 表示本地缺失");
        assertNotNull(record.getCreateTime());
        assertTrue(summary.contains("缺失：1"), "summary 应统计缺失 1 笔，实际：" + summary);
        assertEquals(4, heartbeatRecord().getStatus(), "存在差异时心跳 status 应为 4");
    }

    @Test
    @DisplayName("银行流水缺少 transNo 时无法定位，只计数不落差异记录")
    void bankFlowMissingTransNo_countedAsAbnormal_notPersisted() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE)))
                .thenReturn(Collections.singletonList(bankFlow(null, 1, 42L, "50.00")));
        when(transFlowMapper.selectList(any())).thenReturn(Collections.emptyList());

        String summary = reconciliationService.reconcileWithBank(BIZ_DATE);

        assertTrue(diffRecords().isEmpty(), "缺少 transNo 的流水不应产生差异记录");
        assertTrue(summary.contains("无法比对：1"), "summary 应统计无法比对 1 笔，实际：" + summary);
    }

    @Test
    @DisplayName("P1-9 金额与单号全部匹配 → 无差异记录，但必须落 status=0 的对账心跳")
    void allMatched_noDiffRecord_butHeartbeatWritten() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE)))
                .thenReturn(Collections.singletonList(bankFlow("T1", 3, 42L, "100.00")));
        when(transFlowMapper.selectList(any()))
                .thenReturn(Collections.singletonList(localFlow("T1", 3, 42L, "100.00")));

        String summary = reconciliationService.reconcileWithBank(BIZ_DATE);

        assertTrue(diffRecords().isEmpty(), "不应有差异记录");
        assertTrue(summary.contains("匹配：1"), "summary 应统计匹配 1 笔，实际：" + summary);
        assertTrue(summary.contains("不一致：0") && summary.contains("缺失：0"), "不应有差异，实际：" + summary);

        // 本条是 P1-9 的核心：没有差异也必须有痕迹，否则无法区分"没差异"和"没跑过"
        ReconciliationRecord heartbeat = heartbeatRecord();
        assertEquals(0, heartbeat.getStatus(), "无差异时心跳 status 应为 0");
        assertEquals("对账心跳", heartbeat.getTransTypeName());
        assertEquals(-1, heartbeat.getTransType(), "心跳的 trans_type 占位值应为 -1");
        assertTrue(heartbeat.getRemark().contains("匹配：1"),
                "心跳的 remark 应带完整摘要，实际：" + heartbeat.getRemark());
    }

    @Test
    @DisplayName("P1-9 同一天重复对账 → 心跳就地更新，不堆积重复行")
    void heartbeatIsUpsertedPerDate() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE)))
                .thenReturn(Collections.singletonList(bankFlow("T1", 3, 42L, "100.00")));
        when(transFlowMapper.selectList(any()))
                .thenReturn(Collections.singletonList(localFlow("T1", 3, 42L, "100.00")));

        ReconciliationRecord existing = new ReconciliationRecord();
        existing.setId(999L);
        existing.setTransNo(HEARTBEAT_NO);
        existing.setCreateTime(LocalDateTime.of(2026, 9, 30, 3, 30));
        when(baseMapper.selectOne(any())).thenReturn(existing);

        reconciliationService.reconcileWithBank(BIZ_DATE);

        verify(baseMapper, never()).insert(any(ReconciliationRecord.class));
        verify(baseMapper, times(1)).updateById(existing);
        assertEquals(0, existing.getStatus(), "重跑同一天应把心跳刷新为最新结论");
        assertEquals(LocalDateTime.of(2026, 9, 30, 3, 30), existing.getCreateTime(), "重跑不应改写创建时间");
    }

    @Test
    @DisplayName("金额不一致 → 落 status=2 的差异记录，并带双方金额")
    void amountMismatch_writesRecordWithStatus2() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE)))
                .thenReturn(Collections.singletonList(bankFlow("T2", 3, 42L, "100.00")));
        when(transFlowMapper.selectList(any()))
                .thenReturn(Collections.singletonList(localFlow("T2", 3, 42L, "88.00")));

        reconciliationService.reconcileWithBank(BIZ_DATE);

        List<ReconciliationRecord> diffs = diffRecords();
        assertEquals(1, diffs.size(), "应只有 1 条差异记录（另 1 条为心跳），实际：" + diffs.size());

        ReconciliationRecord record = diffs.get(0);
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

        List<ReconciliationRecord> diffs = diffRecords();
        assertEquals(2, diffs.size(), "应有 2 条差异记录（另 1 条为心跳），实际：" + diffs.size());

        ReconciliationRecord localOnly = diffs.stream()
                .filter(r -> "L1".equals(r.getTransNo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应存在本地多出的差异记录 L1"));
        assertEquals("N/A", localOnly.getBankTransNo());
        assertEquals(1, localOnly.getStatus());

        ReconciliationRecord bankOnly = diffs.stream()
                .filter(r -> "B1".equals(r.getTransNo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应存在银行多出的差异记录 B1"));
        assertEquals("B1", bankOnly.getBankTransNo());
        assertEquals(1, bankOnly.getStatus());
    }

    @Test
    @DisplayName("P1-9 银行流水为空 → 无差异记录，但必须落 status=3 的异常心跳")
    void emptyBankFlows_writesAbnormalHeartbeat() {
        when(bankAccountSyncService.queryBankFlow(eq(TARGET_DATE))).thenReturn(Collections.emptyList());

        String summary = reconciliationService.reconcileWithBank(BIZ_DATE);

        assertTrue(summary.contains("银行流水为空"), "实际：" + summary);
        verify(transFlowMapper, never()).selectList(any());
        assertTrue(diffRecords().isEmpty(), "银行流水为空时不产生差异记录");

        ReconciliationRecord heartbeat = heartbeatRecord();
        assertEquals(3, heartbeat.getStatus(), "银行流水为空时心跳 status 应为 3（异常，未比对）");
        assertTrue(heartbeat.getRemark().contains("银行流水为空"), "实际：" + heartbeat.getRemark());
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

        assertTrue(diffRecords().isEmpty(), "缺少 transNo 的本地流水应被跳过，不产生差异记录");
        assertTrue(summary.contains("匹配：1"), "实际：" + summary);
    }
}
