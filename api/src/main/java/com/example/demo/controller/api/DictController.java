package com.example.demo.controller.api;

import com.example.demo.entity.Dict;
import com.example.demo.service.DictService;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台字典接口
 * 供前端下拉框拉取选项数据（需登录）
 */
@Api(tags = "前台字典")
@RestController
@RequestMapping("/api/core/dict")
@Slf4j
public class DictController {

    @Autowired
    private DictService dictService;

    @ApiOperation("查询全部分类列表")
    @GetMapping("/categories")
    public PccAjaxResult categories() {
        List<Dict> list = dictService.listCategories();
        return new PccAjaxResult(200, "查询成功", list);
    }

    @ApiOperation("按分类编码查选项列表（前端下拉框用）")
    @GetMapping("/listByCode")
    public PccAjaxResult listByCode(@ApiParam("分类编码：returnMethod/education/industry/marry/relation/period 等") @RequestParam String dictCode) {
        List<Dict> list = dictService.listByDictCode(dictCode);
        return new PccAjaxResult(200, "查询成功", list);
    }
}
