package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.dto.AccountInfoDto;

import java.math.BigDecimal;

public interface UserAccountService extends IService<UserAccount> {

    UserAccount getUserById(Long id);

    void recharge(Long userId, BigDecimal amount);

    void withdraw(Long userId, BigDecimal amount);

    BigDecimal getAmount(Long userId);

    /**
     * 组装个人中心账户信息（用户资料 + 余额 + 绑卡状态）
     */
    AccountInfoDto getAccountInfo(Long userId);

    /**
     * 发起充值：校验绑卡 → 组装旺旺银行请求参数+签名+自动提交表单
     * @return 跳转到银行页面的 HTML 表单串
     */
    String buildRechargeForm(Long userId, BigDecimal amount);

    /**
     * 发起提现：校验绑卡 → 组装旺旺银行请求参数+签名+自动提交表单
     * @return 跳转到银行页面的 HTML 表单串
     */
    String buildWithdrawForm(Long userId, BigDecimal amount);
}
