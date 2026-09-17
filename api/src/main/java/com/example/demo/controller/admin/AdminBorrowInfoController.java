package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.BorrowInfo;
import com.example.demo.service.BorrowInfoService;
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
 * 后台借款记录管理（对齐前端 /admin/core/borrow/record/** 约定）
 */
@Api(tags = "后台借款记录管理")
@RestController
@RequestMapping("/admin/core/borrow/record")
@Slf4j
public class AdminBorrowInfoController {

    @Autowired
    private BorrowInfoService borrowInfoService;

    @ApiOperation("获取借款记录分页列表")
    @GetMapping("/page")
    public PccAjaxResult page(
            @ApiParam(value = "当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam(value = "每页记录数") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam(value = "借款人姓名模糊搜索") @RequestParam(required = false) String keyword,
            @ApiParam(value = "状态: 0草稿 1审核中 2通过 -1拒绝") @RequestParam(required = false) Integer status) {
        Page<BorrowInfo> result = borrowInfoService.adminPageList(pageNum, pageSize, status);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "获取借款记录列表成功", data);
    }

    @ApiOperation("审核借款申请（通过/拒绝）")
    @PutMapping("/{id}/audit")
    public PccAjaxResult audit(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Integer status = (Integer) body.get("status");
        if (status == null) {
            return new PccAjaxResult(500, "status不能为空");
        }
        boolean ok = borrowInfoService.auditBorrowInfo(id, status);
        boolean approved = Integer.valueOf(2).equals(status);
        return new PccAjaxResult(200, approved ? "审批通过" : "已拒绝", ok);
    }
}
