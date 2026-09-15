package com.example.demo.controller.api;

import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 核心用户接口（供 finance-message 等微服务通过 Feign 调用）
 */
@RestController
@RequestMapping("/api/core/user")
public class CoreUserController {

    @Autowired
    private UserService userService;

    /**
     * 检查手机号是否可注册（未被注册返回 true）
     */
    @GetMapping("/checkMobile/{mobile}")
    public boolean checkMobile(@PathVariable String mobile) {
        return userService.checkMobileAvailable(mobile);
    }
}
