package com.example.demo.client;

import com.example.demo.client.fallback.SMSApiSmsClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "service-sms", fallback = SMSApiSmsClientFallback.class)
public interface SMSApiSmsClient {
    @PostMapping("/api/sms/user/sendCode")
    Object sendCode(@PathVariable("mobile") String mobile);

    @PostMapping("/api/sms/user/checkCode")
    boolean checkCode(@RequestParam("mobile") String mobile, @RequestParam("code") String code);
}