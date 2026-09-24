package com.example.demo.controller.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.service.LendItemService;
import com.example.demo.service.LendService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.exception.ResourceNotFoundException;
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
    public PccAjaxResult invest(
            @PathVariable Long lendId,
            @RequestBody Map<String, Object> body) {
        Integer userIdInt = LoginUserContext.getUserid();
        if (userIdInt == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        Object amountObj = body.get("amount");
        if (amountObj == null) {
            return new PccAjaxResult(400, "投资金额不能为空");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountObj.toString());
        } catch (NumberFormatException e) {
            return new PccAjaxResult(400, "投资金额格式错误");
        }
        try {
            String formHtml = lendItemService.buildInvestForm(lendId, userIdInt.longValue(), amount);
            return new PccAjaxResult(200, "投标表单生成成功", formHtml);
        } catch (Exception e) {
            log.error("投标失败，lendId={}, userId={}, amount={}", lendId, userIdInt, amount, e);
            String errMsg = e.getMessage();
            if (e instanceof ResourceNotFoundException) {
                errMsg = ((ResourceNotFoundException) e).getMsg();
            }
            return new PccAjaxResult(500, "投标失败：" + (errMsg != null ? errMsg : e.getClass().getSimpleName()));
        }
    }

    @ApiOperation("投标调试接口")
    @PostMapping("/investDebug/{lendId}")
    public PccAjaxResult investDebug(
            @PathVariable Long lendId,
            @RequestBody Map<String, Object> body) {
        try {
            Integer userIdInt = LoginUserContext.getUserid();
            Map<String, Object> debugInfo = new HashMap<>();
            debugInfo.put("step", "1_login");
            debugInfo.put("userId", userIdInt);
            if (userIdInt == null) {
                return new PccAjaxResult(401, "请先登录", debugInfo);
            }

            Object amountObj = body.get("amount");
            BigDecimal amount = new BigDecimal(amountObj.toString());
            debugInfo.put("step", "2_amount");
            debugInfo.put("amount", amount);

            com.example.demo.entity.User user = lendItemService.getUserForInvest(userIdInt.longValue());
            debugInfo.put("step", "3_user");
            debugInfo.put("userType", user != null ? user.getUserType() : null);
            debugInfo.put("mobile", user != null ? user.getMobile() : null);

            com.example.demo.entity.UserBind bind = lendItemService.getBindForInvest(userIdInt.longValue());
            debugInfo.put("step", "4_bind");
            debugInfo.put("bindStatus", bind != null ? bind.getStatus() : null);
            debugInfo.put("bindCode", bind != null ? bind.getBindCode() : null);
            debugInfo.put("bindMobile", bind != null ? bind.getMobile() : null);

            com.example.demo.entity.Lend lend = lendService.getById(lendId);
            debugInfo.put("step", "5_lend");
            debugInfo.put("lendStatus", lend != null ? lend.getStatus() : null);
            debugInfo.put("lendUserId", lend != null ? lend.getUserId() : null);
            debugInfo.put("investAmount", lend != null ? lend.getInvestAmount() : null);

            java.math.BigDecimal balance = lendItemService.getBalanceForInvest(userIdInt.longValue());
            debugInfo.put("step", "6_balance");
            debugInfo.put("balance", balance);

            return new PccAjaxResult(200, "调试信息", debugInfo);
        } catch (Exception e) {
            Map<String, Object> errInfo = new HashMap<>();
            errInfo.put("exception", e.getClass().getName());
            String errMsg = e.getMessage();
            if (e instanceof ResourceNotFoundException) {
                errMsg = ((ResourceNotFoundException) e).getMsg();
            }
            errInfo.put("message", errMsg);
            if (e.getCause() != null) {
                errInfo.put("cause", e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            return new PccAjaxResult(500, "调试异常", errInfo);
        }
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