package com.example.demo.service.impl;

import com.example.demo.client.SMSApiSmsClient;
import com.example.demo.entity.User;
import com.example.demo.entity.dto.UserDto;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.UserMapper;
import com.wwfinance.common.result.PccAjaxResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 注册查重单元测试（"一个手机号只能注册一个账号"）。
 *
 * <p>背景：{@code register()} / {@code addMember()} 用
 * {@code getByMobile(mobile) != null} 做查重，这是典型的 check-then-insert ——
 * 两个并发请求会同时通过查重、双双写库。真实事故：手机号 15138019132
 * 被写成 id=33 / id=34 两行（create_time 完全相同）。
 * 修复方式有二：① 应用层查重（顺序请求的友好提示）② 数据库唯一索引
 * {@code uk_mobile}（并发兜底）。本测试把两条防线都钉住。
 *
 * <p>纯 Mockito：不依赖 MySQL / Redis / 短信服务。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("注册手机号查重测试")
class UserRegisterDedupTest {

    private static final String MOBILE = "15138019132";

    @Mock
    private UserMapper baseMapper;

    @Mock
    private SMSApiSmsClient smsApiSmsClient;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private UserDto registerDto() {
        UserDto dto = new UserDto();
        dto.setMobile(MOBILE);
        dto.setCode("123456");
        dto.setPassword("abc123");
        dto.setPasswordto("abc123");
        dto.setUserType(1);
        return dto;
    }

    /** 短信校验通过（Feign 返回 data=true） */
    private void stubSmsOk() {
        when(smsApiSmsClient.checkCode(any(), any()))
                .thenReturn(new PccAjaxResult(200, "ok", Boolean.TRUE));
    }

    @Test
    @DisplayName("register：手机号已存在 → 应用层查重直接拒绝，且不落库")
    void register_existingMobile_rejectedBeforeInsert() {
        stubSmsOk();
        when(baseMapper.selectOne(any())).thenReturn(new User().setMobile(MOBILE));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(registerDto()));

        assertEquals("手机号已经存在", ex.getMessage());
        verify(baseMapper, never()).insert(any());
    }

    @Test
    @DisplayName("register：并发双提交（查重通过但插入撞唯一索引）→ 翻译成业务文案")
    void register_concurrentDuplicate_translatedToBusinessMessage() {
        stubSmsOk();
        when(baseMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(baseMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("Duplicate entry '15138019132' for key 'user.uk_mobile'"));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(registerDto()));

        assertEquals("手机号已经存在", ex.getMessage());
    }

    @Test
    @DisplayName("addMember：手机号已注册 → 拒绝")
    void addMember_existingMobile_rejectedBeforeInsert() {
        when(baseMapper.selectOne(any())).thenReturn(new User().setMobile(MOBILE));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.addMember(MOBILE, "昵称", "姓名", "110101199001011234", 0));

        assertEquals("该手机号已注册", ex.getMessage());
        verify(baseMapper, never()).insert(any());
    }

    @Test
    @DisplayName("addMember：并发双提交 → 翻译成业务文案")
    void addMember_concurrentDuplicate_translatedToBusinessMessage() {
        when(baseMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(baseMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("Duplicate entry '15138019132' for key 'user.uk_mobile'"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.addMember(MOBILE, "昵称", "姓名", "110101199001011234", 0));

        assertEquals("该手机号已注册", ex.getMessage());
    }
}
