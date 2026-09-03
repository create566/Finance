package com.example.demo.controller.admin;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.User;
import com.example.demo.entity.dto.AdminUserQuery;
import com.example.demo.service.UserService;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/*
*后台用户管理
 */
@RestController
@RequestMapping("/admin/core/user")
@Slf4j
public class AdminUserController {

    @Autowired
    private UserService userService;


    @ApiOperation("获取会员分页列表")
    @PostMapping("/list/{page}/{limit}")
    public PccAjaxResult listPage(
            @ApiParam(value = "当前页码", required = true)
            @PathVariable Long page,
            @ApiParam(value = "每页记录数", required = true)
            @PathVariable Long limit,
            @ApiParam(value = "查询对象", required = false)
            @RequestBody(required = false) AdminUserQuery adminUserQuery) {
        Page<User> pageParam = new Page<>(page, limit);
        IPage<User> pageModel = userService.listPage(pageParam, adminUserQuery);
        return new PccAjaxResult(200, "获取会员分页列表成功", pageModel);
    }

}
