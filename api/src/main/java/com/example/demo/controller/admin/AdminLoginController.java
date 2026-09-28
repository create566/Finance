package com.example.demo.controller.admin;

import com.example.demo.service.AdminUserService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Api(tags = "后台管理员登录")
@RestController
@RequestMapping("/admin/core")
@Slf4j
public class AdminLoginController {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @ApiOperation("管理员登录")
    @PostMapping("/login")
    public PccAjaxResult login(@RequestBody AdminLoginDto dto) {
        try {
            String token = adminUserService.login(dto.getUsername(), dto.getPassword());
            return new PccAjaxResult(200, "登录成功", token);
        } catch (RuntimeException e) {
            return new PccAjaxResult(500, e.getMessage());
        }
    }

    @ApiOperation("退出登录")
    @GetMapping("/logout")
    public PccAjaxResult logout() {
        Integer adminId = LoginUserContext.getUserid();
        if (adminId != null) {
            stringRedisTemplate.delete("login:token:admin:" + adminId);
        }
        LoginUserContext.clear();
        return new PccAjaxResult(200, "退出成功");
    }

    @ApiOperation("获取当前管理员信息")
    @GetMapping("/userInfo")
    public PccAjaxResult userInfo() {
        Integer adminId = LoginUserContext.getUserid();
        if (adminId == null) {
            return new PccAjaxResult(401, "未登录");
        }
        Map<String, Object> info = adminUserService.getAdminInfo(adminId.longValue());
        return new PccAjaxResult(200, "查询成功", info);
    }

    @Data
    public static class AdminLoginDto {
        private String username;
        private String password;
    }
}