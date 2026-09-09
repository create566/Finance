package com.example.demo.controller.api;

import com.example.demo.entity.UserBind;
import com.example.demo.service.UserBindService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 银行绑定 Controller
 *
 * 分层约定：Controller 只做「取登录态 → 调 Service → 封装返回」，
 * 实名/验证码校验、银行表单拼装在 UserBindService。
 */
@Api(tags = "银行绑定")
@RestController
@RequestMapping("/api/user")
public class BankBindController {

    @Autowired
    private UserBindService userBindService;

    @ApiOperation("发起绑卡（需先实名认证+短信验证码）")
    @GetMapping("/bind")
    public String bind(@RequestParam(required = false) String code) {
        Long userId = Long.valueOf(LoginUserContext.getUserid());
        return userBindService.buildBindForm(userId, code);
    }

    @ApiOperation("绑卡状态查询")
    @GetMapping("/bindStatus")
    public PccAjaxResult bindStatus() {
        Long userId = Long.valueOf(LoginUserContext.getUserid());
        UserBind userBind = userBindService.getByUserId(userId);
        if (userBind == null) {
            return new PccAjaxResult(500, "未绑定");
        }
        return new PccAjaxResult(200, "查询成功", userBind);
    }
}
