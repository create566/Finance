package com.example.demo.service.impl;

import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.mapper.UserBindMapper;
import com.example.demo.service.MessageService;
import com.example.demo.service.UserService;
import com.example.demo.client.SMSApiSmsClient;
import com.wwfinance.common.utils.BankSignUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserBindServiceImpl 绑卡回调处理单元测试
 * 注意：构造参数必须【先填业务字段、最后 signedParams 生成签名】
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("绑卡回调业务（Service 层）测试")
class UserBindNotifyServiceTest {

    @Mock
    private UserBindMapper userBindMapper;
    @Mock
    private UserService userService;
    @Mock
    private MessageService messageService;
    @Mock
    private SMSApiSmsClient smsApiSmsClient;

    @InjectMocks
    private UserBindServiceImpl userBindService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setMobile("13800138000");
        mockUser.setName("张三");
    }

    /** 先填业务字段，最后生成合法签名 */
    private void sign(Map<String, Object> params) {
        params.put("sign", BankSignUtil.getSign(new HashMap<>(params)));
    }

    @Test
    @DisplayName("绑卡：验签失败 → false，不落库")
    void bindNotify_signInvalid_returnFalse() {
        Map<String, Object> params = new HashMap<>();
        params.put("agentUserId", "13800138000");
        params.put("sign", "invalid-sign");

        assertFalse(userBindService.handleBindNotify(params));

        verify(userBindMapper, never()).insert(any(UserBind.class));
        verify(userBindMapper, never()).updateById(any(UserBind.class));
    }

    @Test
    @DisplayName("绑卡：用户不存在 → false")
    void bindNotify_userNotFound_returnFalse() {
        Map<String, Object> params = new HashMap<>();
        params.put("agentUserId", "13800138000");
        params.put("bindCode", "BIND123");
        params.put("status", "1");
        sign(params);
        when(userService.getByMobile("13800138000")).thenReturn(null);

        assertFalse(userBindService.handleBindNotify(params));

        verify(userBindMapper, never()).insert(any(UserBind.class));
    }

    @Test
    @DisplayName("绑卡：正常回调 → true，绑卡记录字段正确且同步用户绑卡状态")
    void bindNotify_normal_success() {
        Map<String, Object> params = new HashMap<>();
        params.put("agentUserId", "13800138000");
        params.put("bindCode", "BIND123");
        params.put("idCard", "110101199001011234");
        params.put("bankNo", "6222021234567890");
        params.put("personalName", "张三");
        params.put("bankType", "ICBC");
        params.put("status", "1");
        sign(params);
        when(userService.getByMobile("13800138000")).thenReturn(mockUser);
        // bind() 内部：首次绑定查不到旧记录 → insert，再同步 user 表
        when(userBindMapper.selectOne(any())).thenReturn(null);
        when(userBindMapper.insert(any(UserBind.class))).thenReturn(1);
        when(userService.getById(1L)).thenReturn(mockUser);
        when(userService.updateById(any(User.class))).thenReturn(true);

        boolean result = userBindService.handleBindNotify(params);

        assertTrue(result);
        ArgumentCaptor<UserBind> captor = ArgumentCaptor.forClass(UserBind.class);
        verify(userBindMapper).insert(captor.capture());
        UserBind saved = captor.getValue();
        assertEquals(Long.valueOf(1L), saved.getUserId());
        assertEquals("BIND123", saved.getBindCode());
        assertEquals("6222021234567890", saved.getBankNo());
        assertEquals(Integer.valueOf(1), saved.getStatus());
        // 用户绑卡状态同步为已绑
        assertEquals(Integer.valueOf(1), mockUser.getBindStatus());
        assertEquals("BIND123", mockUser.getBindCode());
    }

    @Test
    @DisplayName("绑卡：status=0 → true，绑卡记录 status=0（银行未通过也落库）")
    void bindNotify_statusZero_bindStatusZero() {
        Map<String, Object> params = new HashMap<>();
        params.put("agentUserId", "13800138000");
        params.put("bindCode", "BIND123");
        params.put("status", "0");
        sign(params);
        when(userService.getByMobile("13800138000")).thenReturn(mockUser);
        when(userBindMapper.selectOne(any())).thenReturn(null);
        when(userBindMapper.insert(any(UserBind.class))).thenReturn(1);
        when(userService.getById(1L)).thenReturn(mockUser);
        when(userService.updateById(any(User.class))).thenReturn(true);

        boolean result = userBindService.handleBindNotify(params);

        assertTrue(result);
        ArgumentCaptor<UserBind> captor = ArgumentCaptor.forClass(UserBind.class);
        verify(userBindMapper).insert(captor.capture());
        assertEquals(Integer.valueOf(0), captor.getValue().getStatus());
    }
}
