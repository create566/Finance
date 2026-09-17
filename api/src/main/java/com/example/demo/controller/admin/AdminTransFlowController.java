package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.entity.TransFlow;
import com.example.demo.service.TransFlowService;
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

import java.util.HashMap;
import java.util.Map;

/**
 * 后台流水监控（对齐 PPT slide13 管理端职能：核对 TransFlow 资金流水，确保账实一致）
 */
@Api(tags = "后台流水监控")
@RestController
@RequestMapping("/admin/core/transFlow")
@Slf4j
public class AdminTransFlowController {

    @Autowired
    private TransFlowService transFlowService;

    @ApiOperation("全平台流水分页查询（按用户/类型/用户名筛选）")
    @GetMapping("/page")
    public PccAjaxResult page(
            @ApiParam("当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页记录数") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam("用户ID") @RequestParam(required = false) Long userId,
            @ApiParam("交易类型:1充值2提现3投标4回款5放款6还款") @RequestParam(required = false) Integer transType,
            @ApiParam("用户名/手机号模糊") @RequestParam(required = false) String userName) {
        IPage<TransFlow> result = transFlowService.adminPageList(pageNum, pageSize, userId, transType, userName);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "查询成功", data);
    }
}
