package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.UserIntegral;

public interface UserIntegralService extends IService<UserIntegral> {

    void addIntegral(Long userId, int points, String content);

    /**
     * 扣减积分：逾期违约/还款延迟等场景调用
     * 写入负数积分明细，同时原子扣减 user.integral（不足时归 0）
     */
    void subtractIntegral(Long userId, int points, String content);
}