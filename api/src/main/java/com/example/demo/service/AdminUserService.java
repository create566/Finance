package com.example.demo.service;

import java.util.Map;

/**
 * 后台管理员 Service
 */
public interface AdminUserService {

    /**
     * 管理员登录：查管理员 + 验密码 + 生成 token
     * @return token
     */
    String login(String username, String password);

    /**
     * 获取当前管理员信息
     */
    Map<String, Object> getAdminInfo(Long adminId);
}
