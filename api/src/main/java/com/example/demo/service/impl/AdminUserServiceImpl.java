package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.SysAdmin;
import com.example.demo.mapper.SysAdminMapper;
import com.example.demo.service.AdminUserService;
import com.example.demo.utils.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AdminUserServiceImpl implements AdminUserService {

    @Autowired
    private SysAdminMapper sysAdminMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final long TOKEN_TTL = 604800;

    @Override
    public String login(String username, String password) {
        SysAdmin admin = sysAdminMapper.selectOne(new LambdaQueryWrapper<SysAdmin>()
                .eq(SysAdmin::getUsername, username));
        if (admin == null) {
            throw new RuntimeException("账号不存在");
        }
        if (!passwordEncoder.matches(password, admin.getPassword())) {
            throw new RuntimeException("账号或密码错误");
        }
        String token = TokenUtil.generateAdminToken(admin.getId().toString(), admin.getUsername());
        stringRedisTemplate.opsForValue().set("login:token:" + admin.getId(), token, TOKEN_TTL, TimeUnit.SECONDS);
        log.info("管理员 {} 登录成功", admin.getUsername());
        return token;
    }

    @Override
    public Map<String, Object> getAdminInfo(Long adminId) {
        SysAdmin admin = sysAdminMapper.selectById(adminId);
        if (admin == null) {
            throw new RuntimeException("管理员不存在");
        }
        Map<String, Object> info = new HashMap<>();
        info.put("id", admin.getId());
        info.put("username", admin.getUsername());
        info.put("nickname", admin.getName());
        info.put("avatar", admin.getAvatar());
        return info;
    }
}