package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.ContractTemplate;
import com.example.demo.enums.ContractTypeEnum;
import com.example.demo.service.ContractTemplateService;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台合同模板管理
 */
@Slf4j
@Api(tags = "后台合同模板管理")
@RestController
@RequestMapping("/admin/core/contract/template")
public class AdminContractTemplateController {

    @Autowired
    private ContractTemplateService contractTemplateService;

    @ApiOperation("合同模板分页列表")
    @GetMapping("/page")
    public PccAjaxResult page(
            @ApiParam("合同类型 LOAN/INVEST/PAYMENT") @RequestParam(required = false) String contractType,
            @ApiParam("状态 1启用 0停用") @RequestParam(required = false) Integer status,
            @ApiParam("当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页记录数") @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            Page<ContractTemplate> page = contractTemplateService.pageList(contractType, status, pageNum, pageSize);
            Map<String, Object> data = new HashMap<>();
            data.put("list", page.getRecords());
            data.put("total", page.getTotal());
            return new PccAjaxResult(200, "获取模板列表成功", data);
        } catch (Exception e) {
            log.error("查询模板列表失败: {}", e.getMessage(), e);
            return new PccAjaxResult(500, "查询模板列表失败：" + e.getMessage());
        }
    }

    @ApiOperation("合同类型字典（供前端下拉）")
    @GetMapping("/types")
    public PccAjaxResult types() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ContractTypeEnum t : ContractTypeEnum.values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("code", t.getCode());
            m.put("desc", t.getDesc());
            m.put("defaultTemplateNo", t.getDefaultTemplateNo());
            list.add(m);
        }
        return new PccAjaxResult(200, "获取合同类型成功", list);
    }

    @ApiOperation("模板详情")
    @GetMapping("/{id}")
    public PccAjaxResult show(@PathVariable Long id) {
        ContractTemplate t = contractTemplateService.getById(id);
        if (t == null) {
            return new PccAjaxResult(404, "模板不存在");
        }
        return new PccAjaxResult(200, "获取模板成功", t);
    }

    @ApiOperation("新增模板")
    @PostMapping
    public PccAjaxResult create(@RequestBody ContractTemplate tpl) {
        try {
            return new PccAjaxResult(200, "新增成功", contractTemplateService.saveTemplate(tpl));
        } catch (ResourceNotFoundException e) {
            return new PccAjaxResult(e.getCode(), e.getMsg());
        } catch (Exception e) {
            log.error("新增模板失败: {}", e.getMessage(), e);
            return new PccAjaxResult(500, "新增失败：" + e.getMessage());
        }
    }

    @ApiOperation("修改模板（生成新版本，不覆盖历史）")
    @PutMapping("/{id}")
    public PccAjaxResult update(@PathVariable Long id, @RequestBody ContractTemplate tpl) {
        try {
            return new PccAjaxResult(200, "修改成功，已生成新版本", contractTemplateService.updateTemplate(id, tpl));
        } catch (ResourceNotFoundException e) {
            return new PccAjaxResult(e.getCode(), e.getMsg());
        } catch (Exception e) {
            log.error("修改模板失败: id={}, error={}", id, e.getMessage(), e);
            return new PccAjaxResult(500, "修改失败：" + e.getMessage());
        }
    }

    @ApiOperation("启用/停用模板")
    @PutMapping("/{id}/status")
    public PccAjaxResult toggleStatus(@PathVariable Long id,
                                      @ApiParam("1启用 0停用") @RequestParam Integer status) {
        try {
            contractTemplateService.toggleStatus(id, status);
            return new PccAjaxResult(200, "操作成功", null);
        } catch (ResourceNotFoundException e) {
            return new PccAjaxResult(e.getCode(), e.getMsg());
        }
    }
}
