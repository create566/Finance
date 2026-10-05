package com.example.demo.controller.api;

import com.example.demo.entity.UserBind;
import com.example.demo.entity.User;
import com.example.demo.enums.BorrowerAuthStatusEnum;
import com.example.demo.enums.InvestorAuthStatusEnum;
import com.example.demo.service.BankDatabaseSyncService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Api(tags = "银行绑定")
@RestController
@RequestMapping("/api/user")
public class BankBindController {

    @Autowired
    private UserBindService userBindService;

    @Autowired
    private UserService userService;

    @Autowired
    private BankDatabaseSyncService bankDatabaseSyncService;

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

    @ApiOperation("直接绑卡（输入银行卡号，无需跳转银行）")
    @PostMapping("/bindDirect")
    public PccAjaxResult bindDirect(@RequestBody java.util.Map<String, String> params) {
        Long userId = Long.valueOf(LoginUserContext.getUserid());
        User user = userService.getById(userId);
        if (user == null) {
            return new PccAjaxResult(500, "用户不存在");
        }

        // 实名认证门禁：与前端 profile/index.vue 的 isRealNameAuthed 完全同口径。
        // 原先后端没有这道校验，只靠前端把按钮 disabled —— 直接调接口即可绕过，
        // 未实名的账号也能绑卡（"前端拦截条件必须能在后端找到对应校验"）。
        boolean realNameAuthed =
                InvestorAuthStatusEnum.AUTHED.getCode().equals(user.getInvestAuthStatus())
                        || BorrowerAuthStatusEnum.AUTHED.getCode().equals(user.getBorrowAuthStatus());
        if (!realNameAuthed) {
            return new PccAjaxResult(400, "请先完成实名认证后再绑卡");
        }

        // 持卡人信息一律取【登录用户自己的】实名信息，**不接受请求体指定**：
        // 原实现用 params.getOrDefault("name", user.getName()) / ("idCard", ...)，
        // 等于允许把银行卡绑到任意他人的姓名+身份证号上（身份冒用）。
        String name = user.getName();
        String idCard = user.getIdCard();
        String bankNo = params.get("bankNo");
        String bankType = params.getOrDefault("bankType", "工商银行");

        if (bankNo == null || bankNo.length() < 16 || bankNo.length() > 19) {
            return new PccAjaxResult(500, "请输入正确的银行卡号（16-19位数字）");
        }
        if (!bankNo.matches("\\d+")) {
            return new PccAjaxResult(500, "银行卡号必须为纯数字");
        }
        UserBind userBind = new UserBind();
        userBind.setUserId(userId);
        String bindCode = UUID.randomUUID().toString().replaceAll("-", "");
        userBind.setBindCode(bindCode);
        userBind.setIdCard(idCard);
        userBind.setBankNo(bankNo);
        userBind.setName(name);
        userBind.setBankType(bankType);
        userBind.setMobile(user.getMobile());
        userBind.setStatus(1);
        userBindService.bind(userBind);

        bankDatabaseSyncService.syncBindRecord(bindCode, user.getMobile(), name,
                user.getMobile(), idCard, bankNo, bankType);

        return new PccAjaxResult(200, "绑卡成功");
    }
}