package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.UserIntegral;

public interface UserIntegralService extends IService<UserIntegral> {

    void addIntegral(Long userId, int points, String content);
}