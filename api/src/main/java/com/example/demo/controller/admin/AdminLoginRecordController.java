package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.UserLoginRecord;
import com.example.demo.service.UserLoginRecordService;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台登录日志管理
 */
@Api(tags = "登录日志管理")
@RestController
@RequestMapping("/admin/core/loginLog")
@Slf4j
public class AdminLoginRecordController {

    @Autowired
    private UserLoginRecordService userLoginRecordService;

    @ApiOperation("登录日志分页查询")
    @GetMapping("/page")
    public PccAjaxResult page(
            @ApiParam(value = "当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam(value = "每页记录数") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam(value = "手机号/用户名搜索") @RequestParam(required = false) String keyword,
            @ApiParam(value = "登录结果: 0失败 1成功") @RequestParam(required = false) Integer loginResult) {
        Page<UserLoginRecord> result = userLoginRecordService.adminPageList(pageNum, pageSize, keyword, loginResult);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "查询成功", data);
    }

    @ApiOperation("登录日志详情")
    @GetMapping("/{id}")
    public PccAjaxResult detail(@PathVariable Long id) {
        UserLoginRecord record = userLoginRecordService.getById(id);
        if (record == null) {
            return new PccAjaxResult(500, "记录不存在");
        }
        return new PccAjaxResult(200, "查询成功", record);
    }
}
