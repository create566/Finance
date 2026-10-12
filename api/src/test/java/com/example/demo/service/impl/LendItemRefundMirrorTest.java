package com.example.demo.service.impl;

import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.enums.TransStatusEnum;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.mapper.LendItemMapper;
import com.example.demo.service.BankAccountSyncService;
import com.example.demo.service.BankDatabaseSyncService;
import com.example.demo.service.ContractService;
import com.example.demo.service.LendService;
import com.example.demo.service.MessageService;
import com.example.demo.service.RecommendService;
import com.example.demo.service.TransFlowService;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserIntegralService;
import com.example.demo.service.UserService;
import com.wwfinance.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 退资路径的回归测试（问题编号 <b>P4-19</b> 镜像回补 + <b>bug ④</b> 撤标原子性）。
 *
 * <p><b>缺陷原状一</b>：投标回调 {@code recordInvestFlow} 会执行
 * {@code updateBalance(user, INVEST, amount)}（走 {@code deductAmount}，即"可用额 −amount"），
 * 但退资路径（用户撤标 {@code cancelInvest} / 下架与流标 {@code withdrawAllInvestments}）
 * <b>只翻状态、不调任何 updateBalance</b>。后果是——银行侧已解冻，平台侧镜像没加回，
 * 因此任一次撤标/流标/下架之后，该投资人的 {@code user_account.amount} 都永久偏低，
 * 且 {@code RecommendServiceImpl} 读该镜像做"可投能力"过滤时会误判余额不足。</p>
 *
 * <p><b>缺陷原状二</b>：撤标走的是"读 {@code invest_amount} → 减 → 写回"的读改写，
 * 并发撤标会互相覆盖，标的已投金额永久偏高。</p>
 *
 * <p>本测试锁定修复后的四条不变量：</p>
 * <ol>
 *   <li>投标中(0) / 满标待放款(2) 的记录退资时，必须 <b>回补镜像</b>（{@code RETURN} 类型加钱）；</li>
 *   <li>必须落一条 {@code trans_flow}（成功态 + 备注含退款原因），供与银行逐笔对账；</li>
 *   <li>退资闸门必须是<b>数据库原子 CAS</b>（{@code atomicMarkCancelled}）：抢不到闸门就零副作用短路；</li>
 *   <li>标的已投金额回减必须是<b>原子自减</b>：抢不到就抛异常且不留任何副作用。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("退资镜像回补与撤标原子性测试（P4-19 / bug ④）")
class LendItemRefundMirrorTest {

    private static final Long INVESTOR_ID = 42L;
    private static final Long LEND_ID = 110L;
    private static final Long ITEM_ID = 900L;

    @Mock
    private LendItemMapper baseMapper;
    @Mock
    private LendService lendService;
    @Mock
    private UserService userService;
    @Mock
    private UserAccountService userAccountService;
    @Mock
    private BankAccountSyncService bankAccountSyncService;
    @Mock
    private BankDatabaseSyncService bankDatabaseSyncService;
    @Mock
    private TransFlowService transFlowService;
    @Mock
    private UserBindService userBindService;
    @Mock
    private UserIntegralService userIntegralService;
    @Mock
    private MessageService messageService;
    @Mock
    private ContractService contractService;
    @Mock
    private RecommendService recommendService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private LendItemServiceImpl lendItemService;

    private User investor() {
        User user = new User();
        user.setId(INVESTOR_ID);
        user.setName("投资人张伟");
        user.setMobile("13800000042");
        return user;
    }

    private LendItem investItem(Integer status, BigDecimal amount) {
        LendItem item = new LendItem();
        item.setId(ITEM_ID);
        item.setLendId(LEND_ID);
        item.setInvestUserId(INVESTOR_ID);
        item.setInvestAmount(amount);
        item.setStatus(status);
        return item;
    }

    private Lend fundingLend() {
        Lend lend = new Lend();
        lend.setId(LEND_ID);
        lend.setLendNo("LEND_TEST_110");
        lend.setTitle("测试标的");
        lend.setUserId(33L);
        lend.setAmount(new BigDecimal("10000.00"));
        lend.setInvestAmount(new BigDecimal("1000.00"));
        lend.setStatus(LendStatusEnum.FUNDING.getCode());
        return lend;
    }

    @Test
    @DisplayName("投标中的投资退资 → 抢到闸门 + 翻已撤标 + 回补镜像 + 落退款流水")
    void refundInvestment_restoresMirrorAndWritesFlow() {
        LendItem item = investItem(0, new BigDecimal("1000.00"));
        when(baseMapper.atomicMarkCancelled(ITEM_ID)).thenReturn(1);
        when(userService.getById(INVESTOR_ID)).thenReturn(investor());
        when(transFlowService.save(any(TransFlow.class))).thenReturn(true);

        assertTrue(lendItemService.refundInvestment(item, "标的已下架退款 LEND_TEST_110"),
                "退资应处理成功");

        // ① 状态翻为已撤标(1)
        assertEquals(1, item.getStatus(), "投资记录应被置为已撤标(1)");

        // ② 镜像回补：投标时扣的是 INVEST，退回必须走 RETURN（加钱），金额与投资额一致
        verify(userAccountService).updateBalance(
                eq(INVESTOR_ID), eq(TransTypeEnum.RETURN), eq(new BigDecimal("1000.00")));

        // ③ 落一条成功态退款流水，备注带退款原因，供与银行对账逐笔核对
        ArgumentCaptor<TransFlow> captor = ArgumentCaptor.forClass(TransFlow.class);
        verify(transFlowService).save(captor.capture());
        TransFlow flow = captor.getValue();
        assertEquals(TransTypeEnum.RETURN.getCode(), flow.getTransType(), "流水类型应为投资回款(4)");
        assertEquals(TransStatusEnum.SUCCESS.getCode(), flow.getStatus(), "退款流水应为成功态");
        assertEquals(new BigDecimal("1000.00"), flow.getTransAmount());
        assertEquals("标的已下架退款 LEND_TEST_110", flow.getMemo(), "备注应写清退款原因");
        assertEquals(INVESTOR_ID, flow.getUserId());
        assertNotNull(flow.getTransNo(), "必须生成退款单号");
    }

    @Test
    @DisplayName("满标待放款(2) 的记录退资同样回补镜像（资金不得无限期悬停）")
    void refundInvestment_statusTwo_alsoRestoresMirror() {
        LendItem item = investItem(2, new BigDecimal("2500.00"));
        when(baseMapper.atomicMarkCancelled(ITEM_ID)).thenReturn(1);
        when(userService.getById(INVESTOR_ID)).thenReturn(investor());
        when(transFlowService.save(any(TransFlow.class))).thenReturn(true);

        assertTrue(lendItemService.refundInvestment(item, "超期未签退款 LEND_TEST_110"));

        assertEquals(1, item.getStatus(), "满标记录也应被置为已撤标(1)");
        verify(userAccountService).updateBalance(
                eq(INVESTOR_ID), eq(TransTypeEnum.RETURN), eq(new BigDecimal("2500.00")));
    }

    @Test
    @DisplayName("抢不到退资闸门（并发/重复撤标）→ 零副作用短路，不重复入账")
    void refundInvestment_gateLost_isIdempotent() {
        LendItem item = investItem(1, new BigDecimal("1000.00"));
        // 数据库 CAS 返回 0 行 = 已被别的线程撤标 / 状态不可退资
        when(baseMapper.atomicMarkCancelled(ITEM_ID)).thenReturn(0);

        assertFalse(lendItemService.refundInvestment(item, "重复退款"), "抢不到闸门应返回 false");

        verifyNoInteractions(userAccountService);
        verifyNoInteractions(transFlowService);
    }

    @Test
    @DisplayName("金额为 NULL 的脏数据 → 只翻状态，不产生虚假入账")
    void refundInvestment_nullAmount_flipsStatusOnly() {
        LendItem item = investItem(0, null);
        when(baseMapper.atomicMarkCancelled(ITEM_ID)).thenReturn(1);

        assertTrue(lendItemService.refundInvestment(item, "脏数据退款"));

        assertEquals(1, item.getStatus(), "状态仍应被置为已撤标(1)");
        verifyNoInteractions(userAccountService);
        verifyNoInteractions(transFlowService);
    }

    @Test
    @DisplayName("用户主动撤标 → 先原子回减标的已投额，再回补镜像并通知投资人")
    void cancelInvest_alsoRestoresMirror() {
        LendItem item = investItem(0, new BigDecimal("1000.00"));
        item.setUpdateTime(LocalDateTime.now());
        when(baseMapper.selectById(ITEM_ID)).thenReturn(item);
        when(lendService.getById(LEND_ID)).thenReturn(fundingLend());
        when(baseMapper.atomicCancelInvest(eq(LEND_ID), eq(new BigDecimal("1000.00")))).thenReturn(1);
        when(baseMapper.atomicMarkCancelled(ITEM_ID)).thenReturn(1);
        when(userService.getById(INVESTOR_ID)).thenReturn(investor());
        when(transFlowService.save(any(TransFlow.class))).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        assertTrue(lendItemService.cancelInvest(ITEM_ID, INVESTOR_ID), "撤标应成功");

        // 标的已投金额走数据库原子自减，而不是"读-改-写"
        verify(baseMapper).atomicCancelInvest(eq(LEND_ID), eq(new BigDecimal("1000.00")));
        assertEquals(1, item.getStatus(), "投资记录应被置为已撤标(1)");
        // 与买入对称：扣 INVEST，退 RETURN
        verify(userAccountService).updateBalance(
                eq(INVESTOR_ID), eq(TransTypeEnum.RETURN), eq(new BigDecimal("1000.00")));
        verify(messageService).sendInvestCancelNotice(
                eq(INVESTOR_ID), eq(new BigDecimal("1000.00")), eq("测试标的"));
        verify(recommendService).evictAll();
        // 非投标路径不得误发投标相关通知
        verify(messageService, never()).sendInvestNotice(any(), any(), anyString());
    }

    @Test
    @DisplayName("撤标时标的已投额原子自减失败（已满标/已投额不足）→ 抛异常，且尚无任何副作用")
    void cancelInvest_atomicDecrementFails_failsCleanly() {
        LendItem item = investItem(0, new BigDecimal("1000.00"));
        when(baseMapper.selectById(ITEM_ID)).thenReturn(item);
        when(lendService.getById(LEND_ID)).thenReturn(fundingLend());
        // 0 行 = 标的状态已不是募资中，或已投金额不足以回减（并发下被别人扣走了）
        when(baseMapper.atomicCancelInvest(eq(LEND_ID), eq(new BigDecimal("1000.00")))).thenReturn(0);

        assertThrows(ResourceNotFoundException.class,
                () -> lendItemService.cancelInvest(ITEM_ID, INVESTOR_ID),
                "原子回减失败必须抛异常，不能继续往下退款");

        // 回减放在最前面 ⇒ 失败时尚未回补镜像、尚未落退款流水、尚未失效缓存
        verifyNoInteractions(userAccountService);
        verifyNoInteractions(transFlowService);
        verifyNoInteractions(recommendService);
    }
}
