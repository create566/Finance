package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.User;
import com.example.demo.entity.UserIntegral;
import com.example.demo.mapper.UserIntegralMapper;
import com.example.demo.service.UserIntegralService;
import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserIntegralServiceImpl extends ServiceImpl<UserIntegralMapper, UserIntegral> implements UserIntegralService {

    @Autowired
    private UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addIntegral(Long userId, int points, String content) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        if (points == 0) {
            return;
        }

        UserIntegral record = new UserIntegral();
        record.setUserId(userId);
        record.setIntegral(points);
        record.setContent(content);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        save(record);

        User user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        int current = user.getIntegral() == null ? 0 : user.getIntegral();
        user.setIntegral(current + points);
        userService.updateById(user);
    }
}