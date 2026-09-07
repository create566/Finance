package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.dto.AdminUserQuery;

import java.math.BigDecimal;
import java.util.List;

public interface UserAccountService extends IService<UserAccount> {

    UserAccount getUserById(Long id);

    void recharge(Long userId, BigDecimal amount);

    void withdraw(Long userId, BigDecimal amount);

    BigDecimal getAmount(Long userId);
}
