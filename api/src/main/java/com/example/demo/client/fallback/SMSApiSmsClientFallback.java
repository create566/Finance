package com.example.demo.client.fallback;


import com.example.demo.client.SMSApiSmsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SMSApiSmsClientFallback  implements SMSApiSmsClient {
    @Override
    public String sendMsg(String mobile){
        log.error("远程调用失败，服务熔断");
        return "发送失败";
    }
}
