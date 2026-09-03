package com.wwfinance.util;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class Msg {
    //生产环境请求地址
    @Value("${SMSUtil.serverIp}")
    private String serverIp;

    //请求端口
    @Value("${SMSUtil.serverPort}")
    private String serverPort;

    //    免费开发测试使用的模板ID为1
    @Value("${SMSUtil.templateId}")
    private String templateId;

    //    应用的APPID
    @Value("${SMSUtil.appId}")
    private String appId;

    //    开发者主账号ACCOUNT SID
    @Value("${SMSUtil.accountSId}")
    private String accountSId;

    //    主账号令牌AUTH TOKEN
    @Value("${SMSUtil.accountToken}")
    private String accountToken;

}
