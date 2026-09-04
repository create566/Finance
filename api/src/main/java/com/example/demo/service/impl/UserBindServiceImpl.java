package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.UserBind;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserBindMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserBindServiceImpl extends ServiceImpl<UserBindMapper, UserBind> implements UserBindService {

    @Autowired
    private UserBindMapper userBindMapper;

    @Autowired
    private UserService userService;

    @Override
    public UserBind getByUserId(Long userId) {
        LambdaQueryWrapper<UserBind> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBind::getUserId, userId);
        return userBindMapper.selectOne(queryWrapper);
    }

    @Override
    public boolean isBind(String idCard) {
        LambdaQueryWrapper<UserBind> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBind::getIdCard, idCard);
        queryWrapper.eq(UserBind::getStatus, 1);
        return userBindMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    public UserBind bind(UserBind userBind) {
        // 已绑定过：更新字段（防银行重复回调撞唯一键）
        UserBind exist = getByUserId(userBind.getUserId());
        if (exist != null) {
            exist.setBindCode(userBind.getBindCode());
            exist.setIdCard(userBind.getIdCard());
            exist.setBankNo(userBind.getBankNo());
            if (userBind.getName() != null) {
                exist.setName(userBind.getName());
            }
            if (userBind.getBankType() != null) {
                exist.setBankType(userBind.getBankType());
            }
            if (userBind.getMobile() != null) {
                exist.setMobile(userBind.getMobile());
            }
            exist.setStatus(userBind.getStatus());
            userBindMapper.updateById(exist);
        } else {
            userBindMapper.insert(userBind);
        }
        User user = userService.getById(userBind.getUserId());
        if (user != null) {
            user.setBindStatus(1);
            user.setBindCode(userBind.getBindCode());
            userService.updateById(user);
        }
        return userBind;
    }

    @Override
    public void updateBindStatus(Long userId, Integer status, String bindCode) {
        UserBind userBind = getByUserId(userId);
        if (userBind != null) {
            userBind.setStatus(status);
            userBind.setBindCode(bindCode);
            userBindMapper.updateById(userBind);
            User user = userService.getById(userId);
            if (user != null) {
                user.setBindStatus(status);
                user.setBindCode(bindCode);
                userService.updateById(user);
            }
        }
    }
}