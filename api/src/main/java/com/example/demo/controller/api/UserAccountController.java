package com.example.demo.controller.api;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.UserBind;
import com.example.demo.entity.dto.AccountInfoDto;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;


@Api(tags = "用户账户")
@RestController
@RequestMapping("/api/user/account")
@Log4j2
public class UserAccountController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private UserBindService userBindService;

    @ApiOperation("查询账户信息（个人中心）")
    @GetMapping("/auth/getAccount")
    public PccAjaxResult getAccount(
            @RequestHeader("Authorization") String authorizationHeader) {

        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(500, "未登录");
        }

        User user = userService.getById(userId.longValue());
        if (user == null) {
            return new PccAjaxResult(500, "用户不存在");
        }

        UserAccount userAccount = userAccountService.getUserById(user.getId());
        UserBind userBind = userBindService.getByUserId(user.getId());

        AccountInfoDto dto = new AccountInfoDto();
        dto.setUserId(user.getId());
        dto.setMobile(user.getMobile());
        dto.setName(user.getName());
        dto.setIdCard(user.getIdCard());
        dto.setUserType(user.getUserType());
        dto.setBorrowAuthStatus(user.getBorrowAuthStatus());
        dto.setAmount(userAccount != null ? userAccount.getAmount() : BigDecimal.ZERO);
        dto.setFreezeAmount(userAccount != null ? userAccount.getFreezeAmount() : BigDecimal.ZERO);
        dto.setBindStatus(userBind != null ? userBind.getStatus() : 0);
        dto.setBindCode(userBind != null ? userBind.getBindCode() : null);

        return new PccAjaxResult(200, "查询成功", dto);
    }
}