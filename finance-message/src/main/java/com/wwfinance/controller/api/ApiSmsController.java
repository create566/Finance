package com.wwfinance.controller.api;

import com.wwfinance.client.CoreUserInfoClient;
import com.wwfinance.common.result.PccAjaxResult;
import com.wwfinance.common.utils.SmsCodeUtil;
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
@Slf4j
public class ApiSmsController {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private CoreUserInfoClient coreUserInfoClient;

    @ApiOperation("发送验证码")
    @PostMapping("/sendCode")
    public PccAjaxResult sendCode(
            @ApiParam(value = "手机号", required = true)
            @RequestParam String mobile) {
        if (mobile == null) {
            return new PccAjaxResult(500, "手机号不能为空");
        }

        // 注：不再调用 checkMobile 查重——注册接口自己会查重；
        // 绑卡等场景需要给"已注册"用户发验证码，查重会导致已注册用户永远发不了码
        String code = SmsCodeUtil.generateCode(6);
        redisTemplate.opsForValue().set("sms:code:" + mobile, code, 5, TimeUnit.MINUTES);

        log.info("发送验证码成功，手机号={}，验证码={}", mobile, code);
        return new PccAjaxResult(200, "验证码发送成功", code);
    }

    @ApiOperation("校验验证码")
    @PostMapping("/checkCode")
    public boolean checkCode(
            @ApiParam(value = "手机号", required = true) @RequestParam String mobile,
            @ApiParam(value = "验证码", required = true) @RequestParam String code) {
        String key = "sms:code:" + mobile;
        Object cacheCode = redisTemplate.opsForValue().get(key);
        if (cacheCode != null && cacheCode.toString().equals(code)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }
}