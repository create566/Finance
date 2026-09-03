package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.UserAccount;
import com.example.demo.mapper.UserAccountMapper;
import com.example.demo.service.UserAccountService;
import org.springframework.stereotype.Service;

@Service
public class UserAccountServiceimpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

    @Override
    public UserAccount getUserById(Long id) {
        return baseMapper.selectById(id);
    }
}
