package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.Investor;
import com.example.demo.entity.dto.InvestorApprovalDTO;
import com.example.demo.service.InvestorService;
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
 * 后台投资人管理（对齐前端 /admin/core/investor/** 约定）
 */
@Api(tags = "后台投资人管理")
@RestController
@RequestMapping("/admin/core/investor")
@Slf4j
public class AdminInvestorController {

    @Autowired
    private InvestorService investorService;

    @ApiOperation("获取投资人分页列表")
    @GetMapping("/page")
    public PccAjaxResult page(
            @ApiParam(value = "当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam(value = "每页记录数") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam(value = "姓名/手机号/身份证模糊搜索") @RequestParam(required = false) String keyword,
            @ApiParam(value = "认证状态: 0未认证 1认证中 2通过 -1失败") @RequestParam(required = false) Integer auditStatus) {
        Page<Investor> result = investorService.adminPageList(pageNum, pageSize, keyword, auditStatus);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "获取投资人列表成功", data);
    }

    @ApiOperation("审核投资人实名认证（通过/拒绝）")
    @PutMapping("/{id}/audit")
    public PccAjaxResult audit(
            @PathVariable Long id,
            @RequestBody InvestorApprovalDTO dto) {
        dto.setInvestorId(id);
        investorService.approveAuth(dto);
        boolean approved = Integer.valueOf(2).equals(dto.getStatus());
        return new PccAjaxResult(200, approved ? "认证通过" : "认证拒绝");
    }
}
