package com.example.demo.controller.api;

import com.example.demo.entity.LendItemReturn;
import com.example.demo.entity.LendReturn;
import com.example.demo.service.LendReturnService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前端用户还款/回款接口
 */
@Api(tags = "还款管理")
@RestController
@RequestMapping("/api/core/lendReturn")
@Slf4j
public class LendReturnController {

    @Autowired
    private LendReturnService lendReturnService;

    @ApiOperation("借款人查询我的还款计划")
    @GetMapping("/myBorrowList")
    public PccAjaxResult myBorrowList() {
        Integer userIdInt = LoginUserContext.getUserid();
        if (userIdInt == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        List<LendReturn> list = lendReturnService.listByBorrower(userIdInt.longValue());
        return new PccAjaxResult(200, "获取还款计划成功", list);
    }

    @ApiOperation("借款人偿还某一期（跳转旺旺银行）")
    @PutMapping("/repay/{id}")
    public String repay(@PathVariable("id") Long lendReturnId) {
        Integer userIdInt = LoginUserContext.getUserid();
        if (userIdInt == null) {
            throw new RuntimeException("请先登录");
        }
        // 组装银行还款表单并返回，借款人扣款由银行异步回调驱动
        return lendReturnService.buildRepayForm(lendReturnId, userIdInt.longValue());
    }

    @ApiOperation("投资人查询我的回款计划")
    @GetMapping("/myInvestList")
    public PccAjaxResult myInvestList() {
        Integer userIdInt = LoginUserContext.getUserid();
        if (userIdInt == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        List<LendItemReturn> list = lendReturnService.listMyItemReturns(userIdInt.longValue());
        return new PccAjaxResult(200, "获取回款计划成功", list);
    }

    @ApiOperation("查询某一期的投资人回款明细")
    @GetMapping("/itemReturns/{lendReturnId}")
    public PccAjaxResult itemReturns(@PathVariable Long lendReturnId) {
        List<LendItemReturn> list = lendReturnService.listItemReturns(lendReturnId);
        return new PccAjaxResult(200, "获取回款明细成功", list);
    }
}
