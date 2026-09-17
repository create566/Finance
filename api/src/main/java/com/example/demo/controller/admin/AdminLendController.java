package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendReturn;
import com.example.demo.service.LendReturnService;
import com.example.demo.service.LendService;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台标的管理（对齐前端 /admin/core/loan/project/** 约定）
 */
@Api(tags = "后台标的管理")
@RestController
@RequestMapping("/admin/core/loan/project")
@Slf4j
public class AdminLendController {

    @Autowired
    private LendService lendService;

    @Autowired
    private LendReturnService lendReturnService;

    @ApiOperation("获取标的分页列表")
    @GetMapping("/page")
    public PccAjaxResult page(
            @ApiParam("当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页记录数") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam("标的名称/编号模糊搜索") @RequestParam(required = false) String keyword,
            @ApiParam("状态:0待发布 1募资中 2已完成 3逾期 4下架") @RequestParam(required = false) Integer status) {
        Page<Lend> result = lendService.pageList(pageNum, pageSize, keyword, status);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "获取标的列表成功", data);
    }

    @ApiOperation("下架标的")
    @PutMapping("/{id}/offline")
    public PccAjaxResult offline(@PathVariable Long id) {
        boolean ok = lendService.offline(id);
        return new PccAjaxResult(200, "下架成功", ok);
    }

    @ApiOperation("发布标的（待发布→募资中）")
    @PutMapping("/{id}/publish")
    public PccAjaxResult publish(@PathVariable Long id) {
        boolean ok = lendService.publish(id);
        return new PccAjaxResult(200, "发布成功", ok);
    }

    @ApiOperation("满标放款（跳转旺旺银行，余额入账由银行异步回调驱动）")
    @PutMapping("/{id}/makeLoan")
    public String makeLoan(@PathVariable Long id) {
        // 组装银行放款表单并返回，借款人入账由银行异步回调驱动
        return lendReturnService.buildMakeLoanForm(id);
    }

    @ApiOperation("查询标的还款计划")
    @GetMapping("/{id}/returns")
    public PccAjaxResult returns(@PathVariable Long id) {
        List<LendReturn> list = lendReturnService.listByLendId(id);
        return new PccAjaxResult(200, "获取还款计划成功", list);
    }

    @ApiOperation("手动触发逾期扫描（测试/补单用，定时任务每天02:00自动执行）")
    @PostMapping("/overdueCheck")
    public PccAjaxResult overdueCheck() {
        int count = lendReturnService.overdueCheck();
        return new PccAjaxResult(200, "逾期扫描完成，处理记录数：" + count, count);
    }
}
