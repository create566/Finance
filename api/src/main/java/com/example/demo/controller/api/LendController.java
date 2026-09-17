package com.example.demo.controller.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.service.LendItemService;
import com.example.demo.service.LendService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 前端投资人标的接口
 */
@Api(tags = "标的投资")
@RestController
@RequestMapping("/api/core/lend")
@Slf4j
public class LendController {

    @Autowired
    private LendService lendService;

    @Autowired
    private LendItemService lendItemService;

    @ApiOperation("获取募资中的标的列表（投资人浏览）")
    @GetMapping("/list")
    public PccAjaxResult list(
            @ApiParam("当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页记录数") @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Lend> result = lendService.listFundingLends(pageNum, pageSize);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "获取标的列表成功", data);
    }

    @ApiOperation("获取标的详情")
    @GetMapping("/{id}")
    public PccAjaxResult detail(@PathVariable Long id) {
        Lend lend = lendService.getById(id);
        if (lend == null) {
            return new PccAjaxResult(500, "标的不存在");
        }
        return new PccAjaxResult(200, "获取标的详情成功", lend);
    }

    @ApiOperation("投资人投标（跳转旺旺银行）")
    @PostMapping("/invest/{lendId}")
    public String invest(
            @PathVariable Long lendId,
            @RequestBody Map<String, Object> body) {
        Integer userIdInt = LoginUserContext.getUserid();
        if (userIdInt == null) {
            throw new RuntimeException("请先登录");
        }
        Object amountObj = body.get("amount");
        if (amountObj == null) {
            throw new RuntimeException("投资金额不能为空");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountObj.toString());
        } catch (NumberFormatException e) {
            throw new RuntimeException("投资金额格式错误");
        }
        // 组装银行表单并返回，余额扣减由银行异步回调驱动
        return lendItemService.buildInvestForm(lendId, userIdInt.longValue(), amount);
    }

    @ApiOperation("获取标的的投资记录列表")
    @GetMapping("/{lendId}/investments")
    public PccAjaxResult investments(@PathVariable Long lendId) {
        List<LendItem> list = lendItemService.listByLendId(lendId);
        return new PccAjaxResult(200, "获取投资记录成功", list);
    }

    @ApiOperation("查询我的投资记录")
    @GetMapping("/myInvestments")
    public PccAjaxResult myInvestments(
            @ApiParam("当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页记录数") @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        Integer userIdInt = LoginUserContext.getUserid();
        if (userIdInt == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        Page<LendItem> result = lendItemService.pageList(pageNum, pageSize, null, userIdInt.longValue());
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "获取我的投资记录成功", data);
    }
}
