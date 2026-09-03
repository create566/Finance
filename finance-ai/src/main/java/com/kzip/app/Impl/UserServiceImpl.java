package com.kzip.app.Impl;

import com.kzip.app.model.User;
import com.kzip.app.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    @Override
    public User getById(Long id) {
        // 没数据库？直接 new 一个简单对象塞点数据返回！
        User user = new User();
        user.setId(id);                       // 用传入的 ID
        user.setUsername("测试用户_" + id);   // 模拟用户名
        user.setPassword("123456");
        user.setEmail("test" + id + "@demo.com");
        user.setAge(18);
        user.setCreateTime(java.time.LocalDateTime.now());
        user.setUpdateTime(java.time.LocalDateTime.now());
        return user;  // 直接返回这个新对象

    }

    @Override
    public List<User> getAllUsers() {
        return null;
    }

    @Override
    public boolean saveUser(User user) {
        return false;
    }

    @Override
    public boolean updateUser(User user) {
        return false;
    }

    @Override
    public boolean deleteUser(Long id) {
        return false;
    }
}
