package com.example.demo.client;

import com.wwfinance.common.result.PccAjaxResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 短信服务客户端。
 *
 * <p>2026-10-09 修复：原先 url 写死为 {@code http://localhost:8120}，等于绕过了 Nacos
 * 服务发现（网关/其他模块都按服务名走，只有这里直连固定地址）——换端口、加实例都要改代码。
 *
 * <p>现改为占位符且默认空串：Spring Cloud OpenFeign 内部用
 * {@code StringUtils.hasText(url)} 判断，**空串时不使用 url**，自动回退到
 * 服务发现 + 负载均衡（通过 Nacos 解析 service-sms）。
 *
 * <p>⚠️ 行为变化：Nacos 未启动时短信功能将不可用（修复前写死地址时仍可用）。
 * 需要临时退回直连时，设环境变量 {@code SMS_SERVICE_URL=http://localhost:8120} 即可，无需改代码。
 */
@FeignClient(value = "service-sms", url = "${sms.service-url:}")
public interface SMSApiSmsClient {
    @PostMapping("/api/sms/user/sendCode")
    PccAjaxResult sendCode(@RequestParam("mobile") String mobile);

    @PostMapping("/api/sms/user/checkCode")
    PccAjaxResult checkCode(@RequestParam("mobile") String mobile, @RequestParam("code") String code);

    @PostMapping("/api/sms/user/sendNotify")
    PccAjaxResult sendNotify(@RequestParam("mobile") String mobile, @RequestParam("content") String content);
}