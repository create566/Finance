package com.example.demo.controller.api;

import com.example.demo.service.TransFlowService;
import com.example.demo.service.UserBindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BankNotifyController 单元测试
 *
 * 分层后 Controller 只负责「接参 → 调 Service → 应答 success/fail」，
 * 业务分支（验签/校验/幂等/落库）的测试在 Service 层：
 *   @see com.example.demo.service.impl.TransFlowNotifyServiceTest
 *   @see com.example.demo.service.impl.UserBindNotifyServiceTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BankNotifyController 回调转发测试")
class BankNotifyControllerTest {

    @Mock
    private UserBindService userBindService;

    @Mock
    private TransFlowService transFlowService;

    @InjectMocks
    private BankNotifyController controller;

    private Map<String, String> params() {
        Map<String, String> params = new HashMap<>();
        params.put("agentUserId", "13800138000");
        return params;
    }

    @Test
    @DisplayName("绑卡回调：Service 受理成功 → 应答 success")
    void bindNotify_serviceAccepted_returnSuccess() {
        when(userBindService.handleBindNotify(anyMap())).thenReturn(true);

        String result = controller.notify(params());

        assertEquals("success", result);
        verify(userBindService).handleBindNotify(anyMap());
    }

    @Test
    @DisplayName("绑卡回调：Service 受理失败（验签/数据异常）→ 应答 fail，银行会重试")
    void bindNotify_serviceRejected_returnFail() {
        when(userBindService.handleBindNotify(anyMap())).thenReturn(false);

        String result = controller.notify(params());

        assertEquals("fail", result);
    }

    @Test
    @DisplayName("充值回调：Service 受理成功 → 应答 success")
    void rechargeNotify_serviceAccepted_returnSuccess() {
        when(transFlowService.handleRechargeNotify(anyMap())).thenReturn(true);

        String result = controller.rechargeNotify(params());

        assertEquals("success", result);
        verify(transFlowService).handleRechargeNotify(anyMap());
    }

    @Test
    @DisplayName("充值回调：Service 受理失败 → 应答 fail")
    void rechargeNotify_serviceRejected_returnFail() {
        when(transFlowService.handleRechargeNotify(anyMap())).thenReturn(false);

        String result = controller.rechargeNotify(params());

        assertEquals("fail", result);
    }

    @Test
    @DisplayName("提现回调：Service 受理成功 → 应答 success")
    void withdrawNotify_serviceAccepted_returnSuccess() {
        when(transFlowService.handleWithdrawNotify(anyMap())).thenReturn(true);

        String result = controller.withdrawNotify(params());

        assertEquals("success", result);
        verify(transFlowService).handleWithdrawNotify(anyMap());
    }

    @Test
    @DisplayName("提现回调：Service 受理失败 → 应答 fail")
    void withdrawNotify_serviceRejected_returnFail() {
        when(transFlowService.handleWithdrawNotify(anyMap())).thenReturn(false);

        String result = controller.withdrawNotify(params());

        assertEquals("fail", result);
    }
}
