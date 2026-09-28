package com.wwfinance.client.fallback;

import com.wwfinance.client.CoreUserInfoClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CoreUserInfoClientFallback implements CoreUserInfoClient {
    @Override
    public boolean checkMobile(String mobile) {
        log.error("远程调用失败，服务熔断");
        // fail-closed：core 服务不可达时宁可拒绝注册，也不能放行重复手机号
        return true; //按"手机号已重复"处理，阻止注册
    }
}
