package com.example.demo.controller.api;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "借款信息")
@RestController
@RequestMapping("/api/core/borrowInfo")
@Slf4j
public class BorrowInfoController {

    @Autowired
    private UserService userService;

    @ApiOperation("获取当前登录用户的借款申请审批状态")
    @GetMapping("/auth/getBorrowInfoStatus")
    public PccAjaxResult getBorrowerStatus(
            @ApiParam(value = "认证token，格式：5grcs xxx", required = true)
            @RequestHeader("Authorization") String authorization) {
        // 当前登录用户ID由 JwtAuthInterceptor 解析 token 后写入上下文
        Integer userid = LoginUserContext.getUserid();
        log.info("当前登录用户 userid: {}", userid);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getId, userid);
        User user = userService.getOne(queryWrapper);

//        Integer status = borrowInfoService.getStatusByUserId(user.getId());
        return new PccAjaxResult(200, "获取借款申请审批状态", "12");
    }


}
