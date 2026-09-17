package com.example.demo.controller.admin;

import com.example.demo.entity.Dict;
import com.example.demo.service.DictService;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台字典管理
 * 维护下拉枚举等基础数据（分类 + 选项两级树形）
 */
@Api(tags = "后台字典管理")
@RestController
@RequestMapping("/admin/core/dict")
@Slf4j
public class AdminDictController {

    @Autowired
    private DictService dictService;

    @ApiOperation("查询全部分类列表")
    @GetMapping("/categories")
    public PccAjaxResult categories() {
        List<Dict> list = dictService.listCategories();
        return new PccAjaxResult(200, "查询成功", list);
    }

    @ApiOperation("按分类编码查选项列表（后台编辑用）")
    @GetMapping("/listByCode")
    public PccAjaxResult listByCode(@ApiParam("分类编码") @RequestParam String dictCode) {
        List<Dict> list = dictService.listByDictCode(dictCode);
        return new PccAjaxResult(200, "查询成功", list);
    }

    @ApiOperation("新增分类")
    @PostMapping("/category")
    public PccAjaxResult saveCategory(@RequestBody Map<String, String> body) {
        String dictCode = body.get("dictCode");
        String name = body.get("name");
        Dict dict = dictService.saveCategory(dictCode, name);
        return new PccAjaxResult(200, "新增分类成功", dict);
    }

    @ApiOperation("新增选项")
    @PostMapping("/option")
    public PccAjaxResult saveOption(@RequestBody Dict dict) {
        Dict saved = dictService.saveOption(dict.getParentId(), dict.getName(), dict.getValue());
        return new PccAjaxResult(200, "新增选项成功", saved);
    }

    @ApiOperation("更新字典节点")
    @PutMapping("/update")
    public PccAjaxResult update(@RequestBody Dict dict) {
        boolean ok = dictService.updateDict(dict);
        return new PccAjaxResult(200, "修改成功", ok);
    }

    @ApiOperation("删除字典节点（分类删除时连带删除子选项）")
    @DeleteMapping("/remove/{id}")
    public PccAjaxResult remove(@PathVariable Long id) {
        boolean ok = dictService.removeDictById(id);
        return new PccAjaxResult(200, "删除成功", ok);
    }
}
