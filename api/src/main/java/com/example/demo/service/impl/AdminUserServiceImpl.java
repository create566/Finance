package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.SysAdmin;
import com.example.demo.exception.BusinessException;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.example.demo.mapper.SysAdminMapper;
import com.example.demo.service.AdminUserService;
import com.wwfinance.common.auth.AdminRoles;
import com.wwfinance.common.utils.TokenUtil;
import com.wwfinance.common.utils.MD5;
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
            throw new ResourceNotFoundException(404, "账号不存在");
        }
        if (!matchesPassword(password, admin.getPassword())) {
            throw new BusinessException(400, "账号或密码错误");
        }
        if (isMd5Password(admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(password));
            sysAdminMapper.updateById(admin);
        }
        String token = TokenUtil.generateAdminToken(
                admin.getId().toString(), admin.getUsername(), admin.getRole());
        // Redis key 按角色命名空间隔离，避免与前台用户（user 表）主键冲突导致会话互踢
        stringRedisTemplate.opsForValue().set("login:token:admin:" + admin.getId(), token, TOKEN_TTL, TimeUnit.SECONDS);
        log.info("管理员 {} 登录成功", admin.getUsername());
        return token;
    }

    @Override
    public Map<String, Object> getAdminInfo(Long adminId) {
        SysAdmin admin = sysAdminMapper.selectById(adminId);
        if (admin == null) {
            throw new ResourceNotFoundException(404, "管理员不存在");
        }
        Map<String, Object> info = new HashMap<>();
        info.put("id", admin.getId());
        info.put("username", admin.getUsername());
        info.put("nickname", admin.getName());
        info.put("avatar", admin.getAvatar());
        // 前端据此决定是否渲染"放款 / 下架 / 作废合同"等高危按钮，避免点了才被 403。
        // 仅作展示优化，真正的闸门在 JwtAuthInterceptor 的 @RequireAdminRole 校验。
        info.put("role", AdminRoles.normalize(admin.getRole()));
        info.put("superAdmin", AdminRoles.SUPER_ADMIN.equals(AdminRoles.normalize(admin.getRole())));
        return info;
    }

    private boolean isMd5Password(String storedPassword) {
        return storedPassword != null && storedPassword.length() == 32 && storedPassword.matches("[0-9a-f]{32}");
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (isMd5Password(storedPassword)) {
            return MD5.encrypt(rawPassword).equals(storedPassword);
        }
        return passwordEncoder.matches(rawPassword, storedPassword);
    }
}