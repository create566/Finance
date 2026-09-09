package com.example.demo.controller.api;

import com.example.demo.entity.BorrowInfo;
import com.example.demo.entity.dto.BorrowInfoDto;
import com.example.demo.service.BorrowInfoService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "借款信息")
@RestController
@RequestMapping("/api/core/borrowInfo")
@Slf4j
public class BorrowInfoController {

    @Autowired
    private BorrowInfoService borrowInfoService;

    @ApiOperation("提交借款申请（支持保存草稿或正式提交）")
    @PostMapping("/auth/submit")
    public PccAjaxResult submit(
            @RequestBody @Validated BorrowInfoDto dto) {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(500, "未登录");
        }
        log.info("用户 {} 提交借款申请: {}", userId, dto);
        BorrowInfo borrowInfo = borrowInfoService.submitBorrowInfo(userId.longValue(), dto);
        boolean isDraft = dto.getIsDraft() != null && dto.getIsDraft();
        String msg = isDraft ? "借款申请草稿已保存" : "借款申请已提交，等待审核";
        return new PccAjaxResult(200, msg, borrowInfo.getId());
    }

    @ApiOperation("查询当前用户的借款申请列表")
    @GetMapping("/auth/list")
    public PccAjaxResult list() {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(500, "未登录");
        }
        List<BorrowInfo> list = borrowInfoService.listByUser(userId.longValue());
        return new PccAjaxResult(200, "查询成功", list);
    }

    @ApiOperation("获取当前用户最近一次借款申请的状态")
    @GetMapping("/auth/status")
    public PccAjaxResult getStatus() {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(500, "未登录");
        }
        return new PccAjaxResult(200, "查询成功",
                borrowInfoService.getStatusByUser(userId.longValue()));
    }

    @ApiOperation("删除当前用户的借款申请草稿")
    @DeleteMapping("/auth/draft")
    public PccAjaxResult deleteDraft() {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(500, "未登录");
        }
        boolean removed = borrowInfoService.deleteDraft(userId.longValue());
        return new PccAjaxResult(200, removed ? "草稿已删除" : "没有可删除的草稿", null);
    }

}