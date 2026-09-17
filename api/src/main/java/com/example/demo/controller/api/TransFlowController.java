package com.example.demo.controller.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.entity.TransFlow;
import com.example.demo.service.TransFlowService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 交易流水 Controller
 * 仅提供流水查询接口（充值/提现由银行回调驱动，见 BankNotifyController）
 *
 * 路径前缀：/api/core/transFlow
 * 需要登录（JwtAuthInterceptor 拦截，从 LoginUserContext 取 userId）
 */
@Api(tags = "交易流水")
@RestController
@RequestMapping("/api/core/transFlow")
@Log4j2
public class TransFlowController {

    @Autowired
    private TransFlowService transFlowService;

    /**
     * 查询当前用户的交易流水（分页）
     * 前端 GET /api/core/transFlow/auth/list/1/10
     */
    @ApiOperation("交易流水列表（分页）")
    @GetMapping("/auth/list/{page}/{limit}")
    public PccAjaxResult list(
            @ApiParam(value = "页码", required = true, example = "1")
            @PathVariable Long page,
            @ApiParam(value = "每页条数", required = true, example = "10")
            @PathVariable Long limit) {

        // 1. 取当前登录用户
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(500, "未登录");
        }

        // 2. 分页查询流水
        IPage<TransFlow> result = transFlowService.listByUser(userId.longValue(), page, limit);

        // 3. 返回结果（records 是当前页数据，total 是总条数）
        return new PccAjaxResult(200, "查询成功", result);
    }

}
