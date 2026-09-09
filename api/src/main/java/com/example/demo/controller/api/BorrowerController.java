package com.example.demo.controller.api;

import com.example.demo.entity.dto.BorrowerAuthDto;
import com.example.demo.service.BorrowerService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 实名认证（借款人档案）
 * 流程：提交认证（认证中）→ 平台审核（通过/拒绝）→ 通过后成为借款人身份
 *
 * 分层约定：
 *   - 参数格式校验：DTO 注解（@NotBlank/@Pattern）+ @Valid，失败由 GlobalExceptionHandler 返回 400
 *   - 业务规则（用户存在/重复提交/状态流转）：BorrowerService，失败抛异常由全局处理器返回 500
 *   - Controller 只负责：取登录态 → 调 Service → 封装 PccAjaxResult
 */
@Api(tags = "实名认证")
@RestController
@RequestMapping("/api/core/borrower")
public class BorrowerController {

    @Autowired
    private BorrowerService borrowerService;

    @ApiOperation("提交实名认证")
    @PostMapping("/auth/submit")
    public PccAjaxResult submit(@Valid @RequestBody BorrowerAuthDto dto) {
        Long userId = Long.valueOf(LoginUserContext.getUserid());
        Long borrowerId = borrowerService.submitAuth(userId, dto);
        return new PccAjaxResult(200, "认证资料已提交，等待审核", borrowerId);
    }

    @ApiOperation("查询当前用户认证状态")
    @GetMapping("/auth/status")
    public PccAjaxResult status() {
        Long userId = Long.valueOf(LoginUserContext.getUserid());
        return new PccAjaxResult(200, "查询成功", borrowerService.getAuthStatus(userId));
    }
}
