package com.example.demo.controller.api;

import com.example.demo.service.UserService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Api(tags = "前台积分等级")
@RestController
@RequestMapping("/api/core/integralGrade")
@Slf4j
public class IntegralGradeController {

    @Autowired
    private UserService userService;

    @ApiOperation("查询当前登录用户可借额度")
    @GetMapping("/borrowAmount")
    public PccAjaxResult getBorrowAmount() {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "未登录");
        }
        Map<String, Object> data = userService.getBorrowAmount(userId.longValue());
        return new PccAjaxResult(200, "查询成功", data);
    }
}
