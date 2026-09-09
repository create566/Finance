package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.entity.IntegralGrade;
import com.example.demo.service.IntegralGradeService;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Api(tags = "后台积分等级管理")
@RestController
@RequestMapping("/admin/core/integralGrade")
@Slf4j
public class AdminIntegralGradeController {

    @Autowired
    private IntegralGradeService integralGradeService;

    @ApiOperation("积分等级列表")
    @GetMapping("/list")
    public PccAjaxResult list() {
        return new PccAjaxResult(200, "查询成功", integralGradeService.list());
    }

    @ApiOperation("分页查询")
    @GetMapping("/page")
    public PccAjaxResult page(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<IntegralGrade> result = integralGradeService.pageList(pageNum, pageSize);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "查询成功", data);
    }

    @ApiOperation("按ID查询")
    @GetMapping("/{id}")
    public PccAjaxResult getById(@PathVariable Long id) {
        return new PccAjaxResult(200, "查询成功", integralGradeService.getById(id));
    }

    @ApiOperation("新增积分等级")
    @PostMapping("/save")
    public PccAjaxResult save(@RequestBody IntegralGrade integralGrade) {
        boolean ok = integralGradeService.saveIntegralGrade(integralGrade);
        return new PccAjaxResult(200, "新增成功", ok);
    }

    @ApiOperation("更新积分等级")
    @PutMapping("/update")
    public PccAjaxResult update(@RequestBody IntegralGrade integralGrade) {
        boolean ok = integralGradeService.updateIntegralGrade(integralGrade);
        return new PccAjaxResult(200, "修改成功", ok);
    }

    @ApiOperation("删除积分等级")
    @DeleteMapping("/remove/{id}")
    public PccAjaxResult remove(@PathVariable Long id) {
        boolean ok = integralGradeService.removeById(id);
        return new PccAjaxResult(200, "删除成功", ok);
    }
}