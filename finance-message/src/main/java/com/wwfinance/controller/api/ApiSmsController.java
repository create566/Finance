package com.wwfinance.controller.api;

import com.wwfinance.client.CoreUserInfoClient;
import com.wwfinance.common.result.PccAjaxResult;
import com.wwfinance.util.SMSUtil;
import com.wwfinance.util.ValidateCodeUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/sms/user")
@Api(tags = "短信管理")
//@CrossOrigin //跨域
@Slf4j
public class ApiSmsController {

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private SMSUtil smsUtil;

    @Resource
    private CoreUserInfoClient coreUserInfoClient;

    @ApiOperation("获取验证码")
    @PostMapping("/sendSMS")
    public PccAjaxResult send(
            @ApiParam(value = "手机号", required = true)
            @RequestParam String mobile){

        if(mobile != null) {

            boolean result = coreUserInfoClient.checkMobile(mobile);
            if(!result) {
                return new PccAjaxResult(500, "手机号已被注册");
            }

            // 生成随机的四位验证码
//            String code = ValidateCodeUtils.generateValidateCode(4).toString();
            //调用容联云提供的短信服务API完成发送短信
            String code2 = String.valueOf(ValidateCodeUtils.generateValidateCode(4));
            String code = smsUtil.sendMsg(mobile, code2);

            log.info("code={}", code);
            //将验证码存入redis
            redisTemplate.opsForValue().set("xx:code:" + mobile, code, 5, TimeUnit.MINUTES);
            }
            return new PccAjaxResult(200, "手机验证码短信发送成功");
    }

    @ApiOperation("发送催还款信息")
    @GetMapping("/sendMsg/{mobile}")
    public String sendMsg(
            @ApiParam(value = "手机号", required = true)
            @PathVariable String mobile){
        //调用容联云提供的短信服务API完成发送短信
        String code = "还款时间剩余天数小于等于3天，请尽快还款";
        String msg = smsUtil.sendMsg(mobile, code);

        return msg;
    }
}
