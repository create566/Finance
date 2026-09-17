package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.Borrower;
import com.example.demo.entity.dto.BorrowerApprovalDTO;
import com.example.demo.service.BorrowerService;
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
 * 后台借款人管理（对齐前端 /admin/core/borrower/** 约定）
 */
@Api(tags = "后台借款人管理")
@RestController
@RequestMapping("/admin/core/borrower")
@Slf4j
public class AdminBorrowerController {

    @Autowired
    private BorrowerService borrowerService;

    @ApiOperation("获取借款人分页列表")
    @GetMapping("/page")
    public PccAjaxResult page(
            @ApiParam(value = "当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam(value = "每页记录数") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam(value = "姓名/手机号/身份证模糊搜索") @RequestParam(required = false) String keyword,
            @ApiParam(value = "认证状态: 0未认证 1认证中 2通过 -1失败") @RequestParam(required = false) Integer auditStatus) {
        Page<Borrower> result = borrowerService.adminPageList(pageNum, pageSize, keyword, auditStatus);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "获取借款人列表成功", data);
    }

    @ApiOperation("审核借款人实名认证（打分+通过/拒绝）")
    @PutMapping("/{id}/audit")
    public PccAjaxResult audit(
            @PathVariable Long id,
            @RequestBody BorrowerApprovalDTO dto) {
        dto.setBorrowerId(id);
        borrowerService.approveAuth(dto);
        boolean approved = Integer.valueOf(2).equals(dto.getStatus());
        return new PccAjaxResult(200, approved ? "认证通过" : "认证拒绝");
    }
}
