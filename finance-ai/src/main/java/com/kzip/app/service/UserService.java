package com.kzip.app.service;

import com.kzip.app.model.User;

import java.util.List;

public interface UserService {

    // 根据ID查询用户
    User getById(Long id);

    // 查询所有用户
    List<User> getAllUsers();

    // 新增用户
    boolean saveUser(User user);

    // 更新用户
    boolean updateUser(User user);

    // 删除用户
    boolean deleteUser(Long id);
}
