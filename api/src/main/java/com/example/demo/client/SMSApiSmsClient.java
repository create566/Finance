package com.example.demo.client;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public interface SMSApiSmsClient {
    @GetMapping("/api/sms/user/sendMsg/{mobile}")
    String sendMsg( @PathVariable String mobile);
}
