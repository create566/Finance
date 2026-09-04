package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.UserBind;

public interface UserBindService extends IService<UserBind> {

    UserBind getByUserId(Long userId);

    boolean isBind(String idCard);

    UserBind bind(UserBind userBind);

    void updateBindStatus(Long userId, Integer status, String bindCode);
}