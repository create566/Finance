package com.example.demo.service.impl;

import com.example.demo.entity.Contract;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.enums.ContractStatusEnum;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.mapper.LendMapper;
import com.example.demo.service.ContractService;
import com.example.demo.service.LendItemService;
import com.example.demo.service.MessageService;
import com.example.demo.service.RecommendService;
import com.wwfinance.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 标的"下架闭环"单元测试。
 *
 * <p>背景：{@code offline()} 原先只把 {@code lend.status} 翻成"已下架"，
 * 已投的钱不解冻、合同不作废、双方收不到通知 —— 投资人的资金会一直冻在银行侧，
 * 这是当时唯一会造成"钱挂账"的真缺口。</p>
 *
 * <p>纯 Mockito：不依赖 MySQL / Redis，可离线回归。锁定修复后的四条不变量：
 * 退还投资、作废待签合同、通知双方、清掉剩余额度缓存。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("标的下架闭环测试")
class LendOfflineClosureTest {

    private static final Long ADMIN_ID = 7L;

    @Mock
    private LendMapper baseMapper;
    @Mock
    private LendItemService lendItemService;
    @Mock
    private ContractService contractService;
    @Mock
    private MessageService messageService;
    @Mock
    private RecommendService recommendService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private LendServiceImpl lendService;

    private Lend lend(Long id, Integer status) {
        Lend lend = new Lend();
        lend.setId(id);
        lend.setLendNo("LEND_TEST_" + id);
        lend.setTitle("测试标的");
        lend.setUserId(33L);
        lend.setAmount(new BigDecimal("10000.00"));
        lend.setInvestAmount(new BigDecimal("1000.00"));
        lend.setInvestNum(1);
        lend.setStatus(status);
        return lend;
    }

    private LendItem investItem(Long id, Long lendId, Long investor) {
        LendItem item = new LendItem();
        item.setId(id);
        item.setLendId(lendId);
        item.setInvestUserId(investor);
        item.setInvestAmount(new BigDecimal("1000.00"));
        item.setStatus(0);
        return item;
    }

    private Contract pendingContract(Long id) {
        Contract c = new Contract();
        c.setId(id);
        c.setContractNo("CT_TEST_" + id);
        c.setStatus(ContractStatusEnum.PENDING.getCode());
        return c;
    }

    @Test
    @DisplayName("下架募资中且有投资的标的 → 退还投资 + 清缓存 + 作废合同 + 通知双方")
    void offline_fundingLendWithInvestments_refundsRevokesAndNotifies() {
        Long lendId = 110L;
        Lend target = lend(lendId, LendStatusEnum.FUNDING.getCode());
        LendItem item = investItem(200L, lendId, 42L);

        when(baseMapper.selectById(lendId)).thenReturn(target);
        when(baseMapper.updateById(any(Lend.class))).thenReturn(1);
        when(lendItemService.list(any())).thenReturn(Collections.singletonList(item));
        when(contractService.list(any())).thenReturn(Collections.singletonList(pendingContract(300L)));

        assertTrue(lendService.offline(lendId, ADMIN_ID), "下架应返回成功");

        // ① 投资记录置为已撤标(1) —— 银行侧据此解冻投资人冻结资金
        assertEquals(1, item.getStatus(), "投资记录应被置为已撤标(1)");
        verify(lendItemService).updateById(item);

        // ② 标的已投金额清零、状态翻为已下架(4)
        assertEquals(0, BigDecimal.ZERO.compareTo(target.getInvestAmount()), "已投金额应清零");
        assertEquals(LendStatusEnum.OFFLINE.getCode(), target.getStatus(), "状态应为已下架(4)");

        // ②b 下架留痕：必须能查到"谁退的投资人资金、什么时候退的"
        assertEquals(ADMIN_ID, target.getOfflineAdminId(), "应记录下架操作人");
        assertFalse(target.getOfflineTime() == null, "应记录下架处置时间");

        // ③ 剩余额度缓存被清 —— 否则下架后缓存里仍留着可投余额
        verify(redisTemplate).delete("lend:leftAmount:" + lendId);

        // ④ 待签署合同被作废
        verify(contractService).revoke(eq(300L), anyString());

        // ⑤ 借款人 + 投资人 各收到一条通知
        verify(messageService).sendLendOfflineNotice(eq(33L), eq("LEND_TEST_110"), any());
        verify(messageService).sendLendOfflineRefundNotice(eq(42L), eq(new BigDecimal("1000.00")), eq("测试标的"));

        // ⑥ 可推荐标的池变化 → 推荐缓存整体失效
        verify(recommendService).evictAll();
    }

    @Test
    @DisplayName("下架待发布标的（尚无投资）→ 仅翻状态并通知借款人，不做退资")
    void offline_pendingLendWithoutInvestments_skipsRefund() {
        Long lendId = 113L;
        Lend target = lend(lendId, LendStatusEnum.PENDING.getCode());
        target.setInvestAmount(BigDecimal.ZERO);

        when(baseMapper.selectById(lendId)).thenReturn(target);
        when(baseMapper.updateById(any(Lend.class))).thenReturn(1);
        when(lendItemService.list(any())).thenReturn(Collections.emptyList());
        when(contractService.list(any())).thenReturn(Collections.emptyList());

        assertTrue(lendService.offline(lendId, ADMIN_ID));

        verify(lendItemService, never()).updateById(any(LendItem.class));
        verify(redisTemplate, never()).delete(anyString());
        verify(messageService, never()).sendLendOfflineRefundNotice(any(), any(), any());
        verify(messageService).sendLendOfflineNotice(eq(33L), eq("LEND_TEST_113"), any());
        assertEquals(LendStatusEnum.OFFLINE.getCode(), target.getStatus());
    }

    @Test
    @DisplayName("合同作废失败不阻断下架（已签署合同不可单方作废）")
    void offline_revokeThrows_loanStillGoesOffline() {
        Long lendId = 111L;
        Lend target = lend(lendId, LendStatusEnum.FUNDING.getCode());

        when(baseMapper.selectById(lendId)).thenReturn(target);
        when(baseMapper.updateById(any(Lend.class))).thenReturn(1);
        when(lendItemService.list(any())).thenReturn(Collections.emptyList());
        when(contractService.list(any())).thenReturn(Collections.singletonList(pendingContract(301L)));
        doThrow(new ResourceNotFoundException(500, "只有待签署的合同才能作废"))
                .when(contractService).revoke(eq(301L), anyString());

        assertTrue(lendService.offline(lendId, ADMIN_ID), "单份合同作废失败不应阻断下架");
        assertEquals(LendStatusEnum.OFFLINE.getCode(), target.getStatus(), "标的仍应下架成功");
    }

    @Test
    @DisplayName("非待发布/募资中/满标待放款的标的不可下架（放款后的标的绝不能退资）")
    void offline_illegalStatus_throws() {
        // 还款中(5) 表示资金已放款给借款人，此时下架退资会导致账目对不上
        Lend target = lend(112L, LendStatusEnum.REPAYING.getCode());
        when(baseMapper.selectById(112L)).thenReturn(target);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> lendService.offline(112L, ADMIN_ID));

        assertEquals("只有待发布/募资中/满标待放款标的才能下架", ex.getMessage());
        // 关键：校验失败必须是"什么都没做"，绝不能误伤资金
        verifyNoInteractions(messageService);
        verifyNoInteractions(lendItemService);
    }

    @Test
    @DisplayName("下架满标待放款标的 → status=2 的投资记录同样要退资（资金不得无限期悬停）")
    void offline_completedLendWithSignedItems_refundsStatusTwoItems() {
        Long lendId = 114L;
        Lend target = lend(lendId, LendStatusEnum.COMPLETED.getCode());
        // 满标时 LendItemServiceImpl 会把该标的全部投资记录统一置为 2
        LendItem item = investItem(201L, lendId, 42L);
        item.setStatus(2);

        when(baseMapper.selectById(lendId)).thenReturn(target);
        when(baseMapper.updateById(any(Lend.class))).thenReturn(1);
        when(lendItemService.list(any())).thenReturn(Collections.singletonList(item));
        when(contractService.list(any())).thenReturn(Collections.emptyList());

        assertTrue(lendService.offline(lendId, ADMIN_ID), "满标待放款标的应允许下架");

        // 撤标范围必须覆盖 status=2，否则这里会静默空跑、投资人资金冻在银行一笔都没退
        assertEquals(1, item.getStatus(), "满标的投资记录也应被置为已撤标(1)");
        verify(lendItemService).updateById(item);
        verify(redisTemplate).delete("lend:leftAmount:" + lendId);
        verify(messageService).sendLendOfflineNotice(eq(33L), eq("LEND_TEST_114"), any());
        verify(messageService).sendLendOfflineRefundNotice(eq(42L), eq(new BigDecimal("1000.00")), eq("测试标的"));
        assertEquals(LendStatusEnum.OFFLINE.getCode(), target.getStatus());
    }

    @Test
    @DisplayName("标的不存在时抛业务异常")
    void offline_lendNotFound_throws() {
        when(baseMapper.selectById(999L)).thenReturn(null);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> lendService.offline(999L, ADMIN_ID));

        assertEquals("标的不存在", ex.getMessage());
    }

    @Test
    @DisplayName("系统自动退资：操作人留 NULL，且发的是「超期未签」而不是「标的已下架」")
    void offlineBySystem_nullOperatorAndTimeoutNotice() {
        Long lendId = 116L;
        Lend target = lend(lendId, LendStatusEnum.COMPLETED.getCode());
        LendItem item = investItem(202L, lendId, 42L);
        item.setStatus(2);

        when(baseMapper.selectById(lendId)).thenReturn(target);
        when(baseMapper.updateById(any(Lend.class))).thenReturn(1);
        when(lendItemService.list(any())).thenReturn(Collections.singletonList(item));
        when(contractService.list(any())).thenReturn(Collections.emptyList());

        assertTrue(lendService.offlineBySystem(lendId), "系统自动退资应成功");

        // ① 操作人为 NULL —— 审计上表示"系统自动处置"，与人工下架可区分
        assertFalse(target.getOfflineAdminId() != null, "offline_admin_id 应留空表示系统处置");
        assertFalse(target.getOfflineTime() == null, "处置时间仍要记录");
        assertEquals(LendStatusEnum.OFFLINE.getCode(), target.getStatus());

        // ② 投资资金确实退了
        assertEquals(1, item.getStatus(), "满标投资记录应被置为已撤标(1)");
        verify(lendItemService).updateById(item);

        // ③ 通知文案必须是"超期未签"，不能让投资人收到莫名的"标的已下架"
        verify(messageService).sendMakeLoanTimeoutNoticeToBorrower(
                eq(33L), eq("LEND_TEST_116"), any());
        verify(messageService).sendMakeLoanTimeoutNoticeToInvestor(
                eq(42L), eq(new BigDecimal("1000.00")), eq("测试标的"));
        verify(messageService, never()).sendLendOfflineNotice(any(), any(), any());
        verify(messageService, never()).sendLendOfflineRefundNotice(any(), any(), any());
    }

    @Test
    @DisplayName("人工下架仍走原通道：操作人非空 + 发「标的已下架」")
    void offline_byAdmin_keepsOriginalNotice() {
        Long lendId = 117L;
        Lend target = lend(lendId, LendStatusEnum.FUNDING.getCode());

        when(baseMapper.selectById(lendId)).thenReturn(target);
        when(baseMapper.updateById(any(Lend.class))).thenReturn(1);
        when(lendItemService.list(any())).thenReturn(Collections.emptyList());
        when(contractService.list(any())).thenReturn(Collections.emptyList());

        assertTrue(lendService.offline(lendId, ADMIN_ID));

        assertEquals(ADMIN_ID, target.getOfflineAdminId());
        verify(messageService).sendLendOfflineNotice(eq(33L), eq("LEND_TEST_117"), any());
        verify(messageService, never()).sendMakeLoanTimeoutNoticeToBorrower(any(), any(), any());
    }

    @Test
    @DisplayName("识别不到操作人时拒绝下架（禁止匿名处置投资人资金）")
    void offline_nullAdminId_throws() {
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> lendService.offline(115L, null));

        assertEquals("无法识别操作人，已拒绝下架：处置投资人资金必须留痕", ex.getMessage());
        // 关键：连库都不许查。识别不到人时任何副作用都不能发生，
        // 否则"先退了钱再发现没操作人"就晚了。
        verifyNoInteractions(baseMapper);
        verifyNoInteractions(lendItemService);
        verifyNoInteractions(contractService);
        verifyNoInteractions(messageService);
    }
}
