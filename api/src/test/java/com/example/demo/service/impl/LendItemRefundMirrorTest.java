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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 退资回补平台镜像余额的回归测试（问题编号 <b>P4-19</b>）。
 *
 * <p><b>缺陷原状</b>：投标回调 {@code recordInvestFlow} 会执行
 * {@code updateBalance(user, INVEST, amount)}（走 {@code deductAmount}，即"可用额 −amount"），
 * 但退资路径（用户撤标 {@code cancelInvest} / 下架与流标 {@code withdrawAllInvestments}）
 * <b>只翻状态、不调任何 updateBalance</b>。后果是——银行侧已解冻，平台侧镜像没加回，
 * 因此任一次撤标/流标/下架之后，该投资人的 {@code user_account.amount} 都永久偏低，
 * 且 {@code RecommendServiceImpl} 读该镜像做"可投能力"过滤时会误判余额不足。</p>
 *
 * <p>本测试锁定修复后的三条不变量：</p>
 * <ol>
 *   <li>投标中(0) / 满标待放款(2) 的记录退资时，必须 <b>回补镜像</b>（{@code RETURN} 类型加钱）；</li>
 *   <li>必须落一条 {@code trans_flow}（成功态 + 备注含退款原因），供与银行逐笔对账；</li>
 *   <li>已撤标(1) 重复调用必须幂等短路，金额为 NULL 的脏数据只翻状态、不产生虚假入账。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("退资回补平台镜像测试（P4-19）")
class LendItemRefundMirrorTest {

    private static final Long INVESTOR_ID = 42L;
    private static final Long LEND_ID = 110L;

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
        item.setId(900L);
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
    @DisplayName("投标中的投资退资 → 翻已撤标 + 回补镜像 + 落退款流水")
    void refundInvestment_restoresMirrorAndWritesFlow() {
        LendItem item = investItem(0, new BigDecimal("1000.00"));
        when(baseMapper.updateById(any(LendItem.class))).thenReturn(1);
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
        when(baseMapper.updateById(any(LendItem.class))).thenReturn(1);
        when(userService.getById(INVESTOR_ID)).thenReturn(investor());
        when(transFlowService.save(any(TransFlow.class))).thenReturn(true);

        assertTrue(lendItemService.refundInvestment(item, "超期未签退款 LEND_TEST_110"));

        assertEquals(1, item.getStatus(), "满标记录也应被置为已撤标(1)");
        verify(userAccountService).updateBalance(
                eq(INVESTOR_ID), eq(TransTypeEnum.RETURN), eq(new BigDecimal("2500.00")));
    }

    @Test
    @DisplayName("已撤标(1) 重复退资 → 幂等短路，不重复入账")
    void refundInvestment_alreadyRefunded_isIdempotent() {
        LendItem item = investItem(1, new BigDecimal("1000.00"));

        assertFalse(lendItemService.refundInvestment(item, "重复退款"), "重复退资应返回 false");

        verifyNoInteractions(userAccountService);
        verifyNoInteractions(transFlowService);
        assertEquals(1, item.getStatus(), "状态应保持已撤标(1)");
    }

    @Test
    @DisplayName("金额为 NULL 的脏数据 → 只翻状态，不产生虚假入账")
    void refundInvestment_nullAmount_flipsStatusOnly() {
        LendItem item = investItem(0, null);
        when(baseMapper.updateById(any(LendItem.class))).thenReturn(1);

        assertTrue(lendItemService.refundInvestment(item, "脏数据退款"));

        assertEquals(1, item.getStatus(), "状态仍应被置为已撤标(1)");
        verifyNoInteractions(userAccountService);
        verifyNoInteractions(transFlowService);
    }

    @Test
    @DisplayName("用户主动撤标 → 同样回补镜像，并通知投资人资金已解冻")
    void cancelInvest_alsoRestoresMirror() {
        LendItem item = investItem(0, new BigDecimal("1000.00"));
        item.setUpdateTime(LocalDateTime.now());
        when(baseMapper.selectById(900L)).thenReturn(item);
        when(lendService.getById(LEND_ID)).thenReturn(fundingLend());
        when(baseMapper.updateById(any(LendItem.class))).thenReturn(1);
        when(userService.getById(INVESTOR_ID)).thenReturn(investor());
        when(transFlowService.save(any(TransFlow.class))).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        assertTrue(lendItemService.cancelInvest(900L, INVESTOR_ID), "撤标应成功");

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
}
