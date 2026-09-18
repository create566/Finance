package com.example.demo.client.fallback;

import com.example.demo.client.SMSApiSmsClient;
import com.wwfinance.common.result.PccAjaxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SMSApiSmsClientFallback implements SMSApiSmsClient {
    @Override
    public PccAjaxResult sendCode(String mobile) {
        log.error("远程调用失败，服务熔断");
        return new PccAjaxResult(500, "短信服务不可用");
    }

    @Override
    public PccAjaxResult checkCode(String mobile, String code) {
        log.error("短信服务不可用，验证码校验失败");
        return new PccAjaxResult(500, "短信服务不可用", false);
    }

    @Override
    public PccAjaxResult sendNotify(String mobile, String content) {
        log.error("短信服务不可用，通知短信发送失败，mobile={}, content={}", mobile, content);
        return new PccAjaxResult(500, "短信服务不可用");
    }
}