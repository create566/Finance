package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.Bank.FormHelper;
import com.example.demo.Bank.HfbConst;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.UserBind;
import com.example.demo.entity.dto.AccountInfoDto;
import com.example.demo.enums.BindStatusEnum;
import com.example.demo.mapper.UserAccountMapper;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.wwfinance.common.utils.BankSignUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Log4j2
@Service
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserBindService userBindService;

    @Override
    public UserAccount getUserById(Long id) {
        return baseMapper.selectById(id);
    }

    @Override
    public void recharge(Long userId, BigDecimal amount) {
        int rows = userAccountMapper.addAmount(userId, amount);
        if (rows == 0) {
            throw new RuntimeException("账户不存在，充值失败");
        }
        log.info("充值成功，userId={}, amount={}", userId, amount);
    }

    @Override
    public void withdraw(Long userId, BigDecimal amount) {
        int rows = userAccountMapper.deductAmount(userId, amount);
        if (rows == 0) {
            throw new RuntimeException("余额不足或账户不存在");
        }
        log.info("提现成功，userId={}, amount={}", userId, amount);
    }

    @Override
    public BigDecimal getAmount(Long userId) {
        UserAccount account = getUserById(userId);
        return account != null ? account.getAmount() : BigDecimal.ZERO;
    }

    @Override
    public AccountInfoDto getAccountInfo(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        UserAccount userAccount = getUserById(userId);
        UserBind userBind = userBindService.getByUserId(userId);

        AccountInfoDto dto = new AccountInfoDto();
        dto.setUserId(user.getId());
        dto.setMobile(user.getMobile());
        dto.setName(user.getName());
        dto.setIdCard(user.getIdCard());
        dto.setUserType(user.getUserType());
        dto.setBorrowAuthStatus(user.getBorrowAuthStatus());
        dto.setAmount(userAccount != null ? userAccount.getAmount() : BigDecimal.ZERO);
        dto.setFreezeAmount(userAccount != null ? userAccount.getFreezeAmount() : BigDecimal.ZERO);
        dto.setBindStatus(userBind != null ? userBind.getStatus() : 0);
        dto.setBindCode(userBind != null ? userBind.getBindCode() : null);
        return dto;
    }

    @Override
    public String buildRechargeForm(Long userId, BigDecimal amount) {
        UserBind userBind = getBoundUser(userId);
        Map<String, Object> paramMap = buildBankParams(userBind, amount,
                HfbConst.RECHARGE_RETURN_URL, HfbConst.RECHARGE_NOTIFY_URL);
        return FormHelper.buildForm(HfbConst.RECHARGE_URL, paramMap);
    }

    @Override
    public String buildWithdrawForm(Long userId, BigDecimal amount) {
        UserBind userBind = getBoundUser(userId);
        Map<String, Object> paramMap = buildBankParams(userBind, amount,
                HfbConst.WITHDRAW_RETURN_URL, HfbConst.WITHDRAW_NOTIFY_URL);
        return FormHelper.buildForm(HfbConst.WITHDRAW_URL, paramMap);
    }

    /**
     * 校验用户存在且已绑卡，返回绑卡记录
     */
    private UserBind getBoundUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        UserBind userBind = userBindService.getByUserId(userId);
        if (userBind == null || !BindStatusEnum.BOUND.getCode().equals(userBind.getStatus())) {
            throw new RuntimeException("请先完成绑卡");
        }
        userBind.setMobile(user.getMobile());
        return userBind;
    }

    /**
     * 组装旺旺银行请求参数并签名
     */
    private Map<String, Object> buildBankParams(UserBind userBind, BigDecimal amount,
                                                 String returnUrl, String notifyUrl) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", HfbConst.AGENT_ID);
        paramMap.put("agentUserId", userBind.getMobile());
        paramMap.put("bindCode", userBind.getBindCode());
        paramMap.put("amount", amount);
        paramMap.put("returnUrl", returnUrl);
        paramMap.put("notifyUrl", notifyUrl);
        paramMap.put("timestamp", new Date().getTime());
        paramMap.put("sign", BankSignUtil.getSign(paramMap));
        return paramMap;
    }
}
