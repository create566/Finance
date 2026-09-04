package com.example.demo.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.dto.UserDto;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserService;
import com.example.demo.utils.TokenUtil;
import com.wwfinance.common.result.PccAjaxResult;
import com.wwfinance.common.utils.MD5;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.example.demo.client.SMSApiSmsClient;

import java.util.List;

@Api(tags = "用户注册")
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private SMSApiSmsClient sMSApiSmsClient;

    @GetMapping("/hello")
    public String index(){
        return "hello hot wap";
    }

    @GetMapping("/{id}")
    public UserDto getUser(@PathVariable Long id) {
       User  user =   userService.getById(id);
       UserDto userDto = new UserDto();
        userDto.setMobile(user.getMobile()) ;
        userDto.setUserType(user.getUserType()) ;
        return userDto;
    }

    @ApiOperation("发送验证码")
    @PostMapping("/sendCode")
    public PccAjaxResult sendCode(@RequestParam("mobile") String mobile) {
        Object result = sMSApiSmsClient.sendCode(mobile);
        return new PccAjaxResult(200, "验证码已发送", result);
    }

    @ApiOperation("验证码登录")
    @PostMapping("/loginByCode")
    public PccAjaxResult loginByCode(@RequestBody UserDto userDTO) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getMobile, userDTO.getMobile());
        User user = userService.getOne(queryWrapper);
        if (user == null) {
            return new PccAjaxResult(500, "用户不存在");
        }
        if (!sMSApiSmsClient.checkCode(userDTO.getMobile(), userDTO.getCode())) {
            return new PccAjaxResult(500, "验证码错误或已过期");
        }
        String token = TokenUtil.generateMerchantToken(user.getId().toString());
        return new PccAjaxResult(200, "登录成功", token);
    }

    @ApiOperation("用户登录")
    @PostMapping("/login")
    public PccAjaxResult login(@RequestBody UserDto userDTO){
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getMobile, userDTO.getMobile());
        User user = userService.getOne(queryWrapper);
        if(user == null){
            return new PccAjaxResult(500, "用户不存在");
        }
        if(!user.getPassword().equals(MD5.encrypt(userDTO.getPassword()))){
            return new PccAjaxResult(500, "密码错误");
        }
        String token = TokenUtil.generateMerchantToken(user.getId().toString());
        return new PccAjaxResult(200, "登录成功", token);
    }

    @ApiOperation("用户注册")
    @PostMapping("/register")
    @Transactional(rollbackFor =Exception.class )
    public PccAjaxResult register(@RequestBody UserDto userDTO){
        if (!sMSApiSmsClient.checkCode(userDTO.getMobile(), userDTO.getCode())) {
            return new PccAjaxResult(500, "验证码错误或已过期");
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getMobile, userDTO.getMobile());
        User user2 = userService.getOne(queryWrapper);
        if(user2 != null){
            return new PccAjaxResult(500, "手机号已经存在11");
        }
        if(!(userDTO.getPassword().equals(userDTO.getPasswordto()))) {
            return new PccAjaxResult(500, "两次输入的密码不正确");
        }
        User user = new User();
        user.setMobile(userDTO.getMobile());
        user.setUserType(userDTO.getUserType());
        user.setName(userDTO.getMobile());
        user.setNickName(userDTO.getMobile());
        user.setPassword(MD5.encrypt(userDTO.getPassword()));
        user.setStatus(1);
        userService.save(user);
        UserAccount userAccount = new UserAccount();
        userAccount.setUserId(user.getId());
        userAccountService.save(userAccount);
        return new PccAjaxResult(200, "注册成功");
    }
}