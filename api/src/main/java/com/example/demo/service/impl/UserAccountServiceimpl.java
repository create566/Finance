package com.example.demo.service.impl;

import com.example.demo.Bank.FormHelper;
import com.example.demo.Bank.HfbConst;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.UserBind;
import com.example.demo.entity.dto.AccountInfoDto;
import com.example.demo.enums.BindStatusEnum;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.mapper.UserAccountMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    @Autowired
    private UserAccountMapper userAccountMapper;

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
            throw new ResourceNotFoundException(404, "用户不存在");
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
        Map<String, Object> paramMap = buildBankParams(userBind, amount, "chargeAmt",
                HfbConst.RECHARGE_RETURN_URL, HfbConst.RECHARGE_NOTIFY_URL);
        return FormHelper.buildForm(HfbConst.RECHARGE_URL, paramMap);
    }

    @Override
    public String buildWithdrawForm(Long userId, BigDecimal amount) {
        UserBind userBind = getBoundUser(userId);
        Map<String, Object> paramMap = buildBankParams(userBind, amount, "fetchAmt",
                HfbConst.WITHDRAW_RETURN_URL, HfbConst.WITHDRAW_NOTIFY_URL);
        return FormHelper.buildForm(HfbConst.WITHDRAW_URL, paramMap);
    }

    private UserBind getBoundUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "用户不存在");
        }
        UserBind userBind = userBindService.getByUserId(userId);
        if (userBind == null || !BindStatusEnum.BOUND.getCode().equals(userBind.getStatus())) {
            throw new BusinessException(400, "请先完成绑卡");
        }
        userBind.setMobile(user.getMobile());
        return userBind;
    }

    private Map<String, Object> buildBankParams(UserBind userBind, BigDecimal amount, String amountKey,
                                                 String returnUrl, String notifyUrl) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", HfbConst.AGENT_ID);
        paramMap.put("agentUserId", userBind.getMobile());
        paramMap.put("bindCode", userBind.getBindCode());
        paramMap.put(amountKey, amount);
        paramMap.put("returnUrl", returnUrl);
        paramMap.put("notifyUrl", notifyUrl);
        paramMap.put("timestamp", new Date().getTime());
        paramMap.put("sign", BankSignUtil.getSign(paramMap));
        return paramMap;
    }

    @Override
    public void updateBalance(Long userId, TransTypeEnum type, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "余额变动金额非法");
        }
        int rows = applyAtomicUpdate(userId, type, amount);
        if (rows == 0) {
            // 0 行：账户不存在（或扣款时余额不足）。先尝试建账户再重试一次
            ensureAccount(userId);
            rows = applyAtomicUpdate(userId, type, amount);
        }
        if (rows == 0) {
            throw new BusinessException(400, "账户余额不足或账户异常，操作失败");
        }
        log.info("账户余额原子更新成功: userId={}, type={}, amount={}",
                userId, type.getDesc(), amount);
    }

    /**
     * 按类型执行原子余额更新（SQL 层 amount = amount ± ?，避免先查后改的并发丢更新）
     *
     * @return 影响行数；-1 表示未知类型（不执行任何更新）
     */
    private int applyAtomicUpdate(Long userId, TransTypeEnum type, BigDecimal amount) {
        switch (type) {
            case RECHARGE:
            case RETURN:
            case LOAN:
                return userAccountMapper.addAmount(userId, amount);
            case WITHDRAW:
            case INVEST:
            case REPAY:
                return userAccountMapper.deductAmount(userId, amount);
            default:
                log.warn("未知的余额变动类型，忽略，type={}", type);
                return -1;
        }
    }

    private void ensureAccount(Long userId) {
        UserAccount existing = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUserId, userId));
        if (existing == null) {
            UserAccount account = new UserAccount();
            account.setUserId(userId);
            account.setAmount(BigDecimal.ZERO);
            account.setFreezeAmount(BigDecimal.ZERO);
            userAccountMapper.insert(account);
        }
    }
}