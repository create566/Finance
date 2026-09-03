package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.dto.AdminUserQuery;

public interface UserAccountService extends IService<UserAccount> {

    UserAccount getUserById(Long id);

}
