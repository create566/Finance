package com.example.demo.service.impl;

import com.example.demo.Bank.FormHelper;
import com.example.demo.Bank.HfbConst;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.entity.dto.AccountInfoDto;
import com.example.demo.enums.BindStatusEnum;
import com.example.demo.service.BankAccountSyncService;
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
public class UserAccountServiceImpl implements UserAccountService {

    @Autowired
    private UserService userService;

    @Autowired
    private UserBindService userBindService;

    @Autowired
    private BankAccountSyncService bankAccountSyncService;

    /**
     * 平台不持有余额，全部从银行查询
     */
    @Override
    public BigDecimal getBalance(Long userId) {
        return bankAccountSyncService.getBankBalance(userId);
    }

    @Override
    public BigDecimal getFreezeAmount(Long userId) {
        return bankAccountSyncService.getBankFreezeAmount(userId);
    }

    @Override
    public AccountInfoDto getAccountInfo(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        BigDecimal balance = bankAccountSyncService.getBankBalance(userId);
        BigDecimal freezeAmount = bankAccountSyncService.getBankFreezeAmount(userId);
        UserBind userBind = userBindService.getByUserId(userId);

        AccountInfoDto dto = new AccountInfoDto();
        dto.setUserId(user.getId());
        dto.setMobile(user.getMobile());
        dto.setName(user.getName());
        dto.setIdCard(user.getIdCard());
        dto.setUserType(user.getUserType());
        dto.setBorrowAuthStatus(user.getBorrowAuthStatus());
        dto.setAmount(balance);
        dto.setFreezeAmount(freezeAmount);
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
