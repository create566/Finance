package com.example.demo.client;

import com.wwfinance.common.result.PccAjaxResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "service-sms", url = "http://localhost:8120")
public interface SMSApiSmsClient {
    @PostMapping("/api/sms/user/sendCode")
    PccAjaxResult sendCode(@RequestParam("mobile") String mobile);

    @PostMapping("/api/sms/user/checkCode")
    PccAjaxResult checkCode(@RequestParam("mobile") String mobile, @RequestParam("code") String code);

    @PostMapping("/api/sms/user/sendNotify")
    PccAjaxResult sendNotify(@RequestParam("mobile") String mobile, @RequestParam("content") String content);
}