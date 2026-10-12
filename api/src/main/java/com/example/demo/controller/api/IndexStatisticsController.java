package com.example.demo.controller.api;

import com.example.demo.service.IndexStatisticsService;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台运营统计（登录 / 注册页展示用）。
 *
 * <p><b>为什么无鉴权</b>：登录页处于未登录状态，拿不到 token；
 * 若该接口要求鉴权，页面只能回退到写死的假数据。因此这里提供<b>只读聚合</b>接口，
 * 并在 {@code MyWebConfig} 的 JWT 白名单中显式放行。</p>
 *
 * <p><b>为什么不担心泄露</b>：只暴露平台级聚合数字（金额合计、笔数、均值、利率区间），
 * 不含任何用户身份、账号、手机号、标的明细等可定位到个人的信息。</p>
 */
@Api(tags = "平台运营统计")
@RestController
@RequestMapping("/api/core/index")
@Slf4j
public class IndexStatisticsController {

    @Autowired
    private IndexStatisticsService indexStatisticsService;

    @ApiOperation("平台运营统计（公开，登录页展示）")
    @GetMapping("/statistics")
    public PccAjaxResult statistics() {
        return new PccAjaxResult(200, "获取平台统计成功", indexStatisticsService.platformStatistics());
    }
}
