package com.example.demo.controller.api;

import com.example.demo.entity.dto.TransAmountDto;
import com.example.demo.service.UserAccountService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 用户账户 Controller
 *
 * 分层约定：Controller 只做「取登录态 → 调 Service → 封装返回」，
 * 账户信息组装、绑卡校验、银行表单拼装均在 UserAccountService。
 */
@Api(tags = "用户账户")
@RestController
@RequestMapping("/api/user/account")
@Log4j2
public class UserAccountController {

    @Autowired
    private UserAccountService userAccountService;

    @ApiOperation("查询账户信息（个人中心）")
    @GetMapping("/auth/getAccount")
    public PccAjaxResult getAccount() {
        Long userId = Long.valueOf(LoginUserContext.getUserid());
        return new PccAjaxResult(200, "查询成功", userAccountService.getAccountInfo(userId));
    }

    @ApiOperation("发起充值（跳转旺旺银行）")
    @PostMapping("/auth/recharge")
    public String recharge(@Valid @RequestBody TransAmountDto dto) {
        Long userId = Long.valueOf(LoginUserContext.getUserid());
        return userAccountService.buildRechargeForm(userId, dto.getAmount());
    }

    @ApiOperation("发起提现（跳转旺旺银行）")
    @PostMapping("/auth/withdraw")
    public String withdraw(@Valid @RequestBody TransAmountDto dto) {
        Long userId = Long.valueOf(LoginUserContext.getUserid());
        return userAccountService.buildWithdrawForm(userId, dto.getAmount());
    }
}
