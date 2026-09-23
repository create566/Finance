package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.User;
import com.example.demo.entity.UserIntegral;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.mapper.UserMapper;
import com.example.demo.mapper.UserIntegralMapper;
import com.example.demo.service.UserIntegralService;
import com.example.demo.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class UserIntegralServiceImpl extends ServiceImpl<UserIntegralMapper, UserIntegral> implements UserIntegralService {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addIntegral(Long userId, int points, String content) {
        if (userId == null) {
            throw new BusinessException(400, "用户ID不能为空");
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
            throw new ResourceNotFoundException(404, "用户不存在");
        }
        int current = user.getIntegral() == null ? 0 : user.getIntegral();
        user.setIntegral(current + points);
        userService.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void subtractIntegral(Long userId, int points, String content) {
        if (userId == null) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        if (points <= 0) {
            return;
        }

        // 写入负数积分明细，保留扣减记录便于追溯
        UserIntegral record = new UserIntegral();
        record.setUserId(userId);
        record.setIntegral(-points);
        record.setContent(content);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        save(record);

        // 原子扣减 user.integral，SQL 内 GREATEST 兜底不会变负数
        int rows = userMapper.deductIntegral(userId, points);
        if (rows == 0) {
            throw new ResourceNotFoundException(404, "用户不存在，扣减积分失败：userId=" + userId);
        }
        log.info("扣减积分成功，userId={}, points={}, content={}", userId, points, content);
    }
}