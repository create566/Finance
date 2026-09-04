package com.example.demo.controller.api;

import com.example.demo.Bank.FormHelper;
import com.example.demo.Bank.HfbConst;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import com.wwfinance.common.utils.BankSignUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Api(tags = "银行绑定")
@RestController
@RequestMapping("/api/user")
public class BankBindController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserBindService userBindService;

    @ApiOperation("发起绑卡")
    @GetMapping("/bind")
    public String bind() {
        Integer userId = LoginUserContext.getUserid();
        User user = userService.getById(userId.longValue());
        if (user == null) {
            return "用户不存在";
        }

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", HfbConst.AGENT_ID);
        paramMap.put("agentUserId", user.getMobile());
        paramMap.put("personalName", user.getName());
        paramMap.put("mobile", user.getMobile());
        paramMap.put("idCard", user.getIdCard());
        paramMap.put("returnUrl", HfbConst.USERBIND_RETURN_URL);
        paramMap.put("notifyUrl", HfbConst.USERBIND_NOTIFY_URL);
        paramMap.put("timestamp", new Date().getTime());

        String sign = BankSignUtil.getSign(paramMap);
        paramMap.put("sign", sign);

        return FormHelper.buildForm(HfbConst.USERBIND_URL, paramMap);
    }

    @ApiOperation("绑卡状态查询")
    @GetMapping("/bindStatus")
    public PccAjaxResult bindStatus() {
        Integer userId = LoginUserContext.getUserid();
        UserBind userBind = userBindService.getByUserId(userId.longValue());
        if (userBind == null) {
            return new PccAjaxResult(500, "未绑定");
        }
        return new PccAjaxResult(200, "查询成功", userBind);
    }
}