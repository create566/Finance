package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.UserAccount;
import com.example.demo.mapper.UserAccountMapper;
import com.example.demo.service.UserAccountService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Log4j2
@Service
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

    @Autowired
    private UserAccountMapper userAccountMapper;

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
}