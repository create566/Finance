package com.example.demo.service.impl;

import com.example.demo.entity.User;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.wwfinance.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 投标身份门槛单元测试（P1-20）。
 *
 * <p>背景：平台两处能力判据历史上**不对称** ——
 * <ul>
 *   <li>借款看 {@code borrow_auth_status==2}（{@code BorrowInfoServiceImpl:68}）</li>
 *   <li>投资看 {@code user_type==1}（{@code LendItemServiceImpl}，本测试覆盖处）</li>
 * </ul>
 * 而 {@code user_type} 只在**注册时**由表单写入，实名认证审批<b>不会</b>覆盖它。于是
 * 「注册成**借款人** → 补做投资人实名认证 → <b>仍然被拒</b>」，与前端认证页写明的
 * "投资人认证（认证后可参与投标）"自相矛盾。
 *
 * <p>修复方案采取**纯加法**：门槛放宽为
 * {@code user_type==1 || invest_auth_status==2}。这样既兑现了前端承诺，
 * 又不影响存量 34 个「注册为投资人但从未做投资人认证」的账号（它们仍可投标）。
 *
 * <p>纯 Mockito：不依赖 MySQL / Redis / 银行接口。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("投标身份门槛测试")
class LendItemInvestGateTest {

    private static final Long USER_ID = 33L;
    private static final Long LEND_ID = 1L;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock
    private UserService userService;

    @Mock
    private UserBindService userBindService;

    @InjectMocks
    private LendItemServiceImpl lendItemService;

    /**
     * 用户实体：userType 1=投资者 2=借款人；investAuthStatus 2=投资人认证通过。
     */
    private User user(Integer userType, Integer investAuthStatus) {
        User user = new User();
        user.setId(USER_ID);
        user.setUserType(userType);
        user.setInvestAuthStatus(investAuthStatus);
        user.setMobile("13800001111");
        return user;
    }

    @Test
    @DisplayName("既不是投资人(user_type≠1)也未做投资人认证 → 拒绝")
    void neitherInvestorTypeNorAuthed_rejected() {
        when(userService.getById(anyLong())).thenReturn(user(2, 0));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> lendItemService.buildInvestForm(LEND_ID, USER_ID, AMOUNT));

        assertTrue(ex.getMessage().contains("投资人实名认证"), "实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("注册为借款人但已完成投资人认证 → 门槛放行（本次修复点）")
    void borrowerWithInvestorAuth_passesGate() {
        when(userService.getById(anyLong())).thenReturn(user(2, 2));
        // 放行后会走到下一道校验（绑卡），未绑卡则抛"请先完成绑卡" —— 用它证明已越过身份门槛
        when(userBindService.getByUserId(anyLong())).thenReturn(null);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> lendItemService.buildInvestForm(LEND_ID, USER_ID, AMOUNT));

        assertEquals("请先完成绑卡", ex.getMessage(),
                "投资人认证通过应越过身份门槛（下一步应轮到绑卡校验），实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("注册为投资人但未做投资人认证 → 仍可投标（存量 34 个账号不受影响）")
    void investorByUserTypeOnly_stillAllowed() {
        when(userService.getById(anyLong())).thenReturn(user(1, 0));
        when(userBindService.getByUserId(anyLong())).thenReturn(null);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> lendItemService.buildInvestForm(LEND_ID, USER_ID, AMOUNT));

        assertEquals("请先完成绑卡", ex.getMessage(),
                "存量投资人不应因本次改动被收紧，实际：" + ex.getMessage());
    }
}
