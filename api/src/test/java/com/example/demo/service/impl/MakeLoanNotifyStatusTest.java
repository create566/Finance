package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItemReturn;
import com.example.demo.entity.LendReturn;
import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
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
import com.wwfinance.common.utils.BankSignUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 满标放款落账的状态码口径（2026-10-08 修）。
 *
 * <p><b>背景：</b>旺旺银行 {@code /userInvest/AgreeAccountLendProject} 是<b>同步</b>接口，
 * 成功时回 {@code resultCode=0000}；而异步回调那套（绑卡/充值/提现/投标）用 {@code "1"} / {@code "0001"}。
 * 平台原先只把 {@code "0001"} 映射成成功，于是银行明明放款成功了，
 * 平台却把它当"非成功"静默丢弃 —— 标的永久卡在「已满标待放款」。
 *
 * <p>本类把这三条口径钉住，防止哪天有人"整理"掉 {@code "0000"} 这个分支。
 */
@ExtendWith(MockitoExtension.class)
class MakeLoanNotifyStatusTest {

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

    @InjectMocks
    private LendReturnServiceImpl service;

    private static final Long LEND_ID = 106L;
    private static final String BIND_CODE = "bind-code-001";
    private static final String MOBILE = "13800001111";

    /** 组装一份能通过验签的银行放款应答（resultCode 可换） */
    private Map<String, Object> notifyParams(String resultCode) {
        Map<String, Object> p = new HashMap<>();
        p.put("agentUserId", MOBILE);
        p.put("bindCode", BIND_CODE);
        p.put("voteAmt", "60000.00");
        p.put("agentBillNo", "MAKELoan20261008160000106");
        p.put("agentProjectCode", String.valueOf(LEND_ID));
        p.put("resultCode", resultCode);
        p.put("sign", BankSignUtil.getSign(p));
        return p;
    }

    private Lend lendOf(Integer status) {
        Lend lend = new Lend();
        lend.setId(LEND_ID);
        lend.setUserId(35L);
        lend.setStatus(status);
        lend.setAmount(new BigDecimal("60000.00"));
        lend.setInvestAmount(new BigDecimal("60000.00"));
        lend.setPeriod(6);
        lend.setLendYearRate(new BigDecimal("12.00"));
        lend.setReturnMethod(1);
        lend.setLendNo("LEND_TEST_106");
        return lend;
    }

    @Test
    @DisplayName("银行同步放款成功码 0000 必须被当成成功并进入落账")
    void resultCode0000_isTreatedAsSuccess() {
        User user = new User();
        user.setId(35L);
        when(userService.getByMobile(MOBILE)).thenReturn(user);
        when(transFlowService.existsByTransNo(any())).thenReturn(false);
        when(lendService.getById(LEND_ID)).thenReturn(lendOf(LendStatusEnum.FUNDING.getCode()));

        assertTrue(service.handleMakeLoanNotify(notifyParams("0000")),
                "resultCode=0000 是银行同步放款接口的成功码，必须进入落账分支");

        // 走到落账 = 一定会二次查标的
        verify(lendService).getById(LEND_ID);
    }

    @Test
    @DisplayName("异步回调老口径 0001 仍然判成功（回归保护）")
    void resultCode0001_stillSuccess() {
        User user = new User();
        user.setId(35L);
        when(userService.getByMobile(MOBILE)).thenReturn(user);
        when(transFlowService.existsByTransNo(any())).thenReturn(false);
        when(lendService.getById(LEND_ID)).thenReturn(lendOf(LendStatusEnum.FUNDING.getCode()));

        assertTrue(service.handleMakeLoanNotify(notifyParams("0001")));
        verify(lendService).getById(LEND_ID);
    }

    @Test
    @DisplayName("银行返回非成功码：直接确认，零副作用（不查库、不写流水）")
    void failedResultCode_isAckedWithoutSideEffect() {
        assertTrue(service.handleMakeLoanNotify(notifyParams("9999")),
                "银行侧失败只需应答 success，不做任何处理");

        verifyNoInteractions(lendService, lendItemService, lendItemReturnMapper,
                userAccountService, userService, userBindService, transFlowService,
                messageService, userIntegralService, baseMapper);
    }

    @Test
    @DisplayName("标的已不是满标待放款状态：落账跳过，绝不重复生成还款计划")
    void notCompletedLend_isSkipped() {
        User user = new User();
        user.setId(35L);
        when(userService.getByMobile(MOBILE)).thenReturn(user);
        when(transFlowService.existsByTransNo(any())).thenReturn(false);
        when(lendService.getById(LEND_ID)).thenReturn(lendOf(LendStatusEnum.REPAYING.getCode()));

        assertTrue(service.handleMakeLoanNotify(notifyParams("0000")));

        //  repay = 还款中 ⇒ 落账必须短路，一条还款计划都不许写
        verify(baseMapper, never()).insert(any(LendReturn.class));
        verify(lendItemReturnMapper, never()).insert(any(LendItemReturn.class));
        verify(transFlowService, never()).save(any(TransFlow.class));
        verify(lendService, never()).updateById(any(Lend.class));
    }
}
