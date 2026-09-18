package com.example.demo.controller.api;

import com.example.demo.entity.User;
import com.example.demo.entity.dto.UserDto;
import com.example.demo.client.SMSApiSmsClient;
import com.example.demo.service.UserService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@Api(tags = "用户接口")
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private SMSApiSmsClient sMSApiSmsClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("/hello")
    public String index() {
        return "hello hot wap";
    }

    @GetMapping("/{id}")
    public UserDto getUser(@PathVariable Long id) {
        User user = userService.getById(id);
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setMobile(user.getMobile());
        userDto.setUserType(user.getUserType());
        userDto.setName(user.getName());
        userDto.setNickName(user.getNickName());
        userDto.setIdCard(user.getIdCard());
        userDto.setEmail(user.getEmail());
        userDto.setHeadImg(user.getHeadImg());
        userDto.setBindStatus(user.getBindStatus());
        userDto.setBindCode(user.getBindCode());
        return userDto;
    }

    @ApiOperation("获取当前登录用户信息")
    @GetMapping("/info")
    public PccAjaxResult info() {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "未登录");
        }
        UserDto userDto = userService.getCurrentUserInfo(userId.longValue());
        return new PccAjaxResult(200, "查询成功", userDto);
    }

    @ApiOperation("修改用户资料")
    @PutMapping("/profile")
    public PccAjaxResult profile(@RequestBody UserDto userDto) {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "未登录");
        }
        userService.updateProfile(userId.longValue(), userDto);
        return new PccAjaxResult(200, "修改成功");
    }

    @ApiOperation("发送验证码")
    @PostMapping("/sendCode")
    public PccAjaxResult sendCode(@RequestParam("mobile") String mobile) {
        PccAjaxResult result = sMSApiSmsClient.sendCode(mobile);
        return result;
    }

    @ApiOperation("用户登录")
    @PostMapping("/login")
    public PccAjaxResult login(@RequestBody UserDto userDTO) {
        try {
            String token = userService.login(userDTO.getMobile(), userDTO.getPassword());
            return new PccAjaxResult(200, "登录成功", token);
        } catch (RuntimeException e) {
            return new PccAjaxResult(500, e.getMessage());
        }
    }

    @ApiOperation("验证码登录")
    @PostMapping("/loginByCode")
    public PccAjaxResult loginByCode(@RequestBody UserDto userDTO) {
        try {
            String token = userService.loginByCode(userDTO.getMobile(), userDTO.getCode());
            return new PccAjaxResult(200, "登录成功", token);
        } catch (RuntimeException e) {
            return new PccAjaxResult(500, e.getMessage());
        }
    }

    @ApiOperation("用户退出登录")
    @PostMapping("/logout")
    public PccAjaxResult logout() {
        Integer userId = LoginUserContext.getUserid();
        if (userId != null) {
            stringRedisTemplate.delete("login:token:" + userId);
        }
        LoginUserContext.clear();
        return new PccAjaxResult(200, "退出成功");
    }

    @ApiOperation("用户注册")
    @PostMapping("/register")
    public PccAjaxResult register(@RequestBody UserDto userDTO) {
        try {
            userService.register(userDTO);
            return new PccAjaxResult(200, "注册成功");
        } catch (RuntimeException e) {
            return new PccAjaxResult(500, e.getMessage());
        }
    }
}