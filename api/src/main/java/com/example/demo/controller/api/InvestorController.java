package com.example.demo.controller.api;

import com.example.demo.entity.dto.InvestorAuthDto;
import com.example.demo.service.InvestorService;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 投资人实名认证
 * 流程：提交认证（认证中）→ 平台审核（通过/拒绝）→ 通过后投资人可正常投标
 *
 * 分层约定：
 *   - 参数格式校验：DTO 注解（@NotBlank/@Pattern）+ @Valid，失败由 GlobalExceptionHandler 返回 400
 *   - 业务规则（用户存在/重复提交/状态流转）：InvestorService，失败抛异常由全局处理器返回 500
 *   - Controller 只负责：取登录态 → 调 Service → 封装 PccAjaxResult
 */
@Api(tags = "投资人实名认证")
@RestController
@RequestMapping("/api/core/investor")
public class InvestorController {

    @Autowired
    private InvestorService investorService;

    @ApiOperation("提交投资人实名认证")
    @PostMapping("/auth/submit")
    public PccAjaxResult submit(@Valid @RequestBody InvestorAuthDto dto) {
        Long userId = Long.valueOf(LoginUserContext.getUserid());
        Long investorId = investorService.submitAuth(userId, dto);
        return new PccAjaxResult(200, "认证资料已提交，等待审核", investorId);
    }

    @ApiOperation("查询当前用户投资人认证状态")
    @GetMapping("/auth/status")
    public PccAjaxResult status() {
        Long userId = Long.valueOf(LoginUserContext.getUserid());
        return new PccAjaxResult(200, "查询成功", investorService.getAuthStatus(userId));
    }
}
