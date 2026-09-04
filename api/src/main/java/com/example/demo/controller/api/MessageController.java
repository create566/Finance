package com.example.demo.controller.api;


import com.example.demo.client.SMSApiSmsClient;
import com.wwfinance.common.result.PccAjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessageController {

    @Autowired
    private SMSApiSmsClient sMSApiSmsClient;
    @GetMapping("/api/sendtest")
    public PccAjaxResult sendsms(@RequestParam(required = false, defaultValue = "222222") String mobile){
        Object msg = sMSApiSmsClient.sendCode(mobile);
        return new PccAjaxResult(200, msg != null ? msg.toString() : "发送失败");
    }
}
