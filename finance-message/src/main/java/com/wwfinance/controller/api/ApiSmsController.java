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

        // 教学演示：验证码不真发短信，直接打印到控制台，方便从控制台复制到前端登录
        System.out.println();
        System.out.println("================  短信验证码  ================");
        System.out.println("  手机号: " + mobile);
        System.out.println("  验证码: " + code);
        System.out.println("  有效期: 5 分钟");
        System.out.println("===============================================");
        System.out.println();
        log.info("发送验证码成功，手机号={}，验证码={}", mobile, code);
        return new PccAjaxResult(200, "验证码已发送", code);
    }

    @ApiOperation("校验验证码")
    @PostMapping("/checkCode")
    public PccAjaxResult checkCode(
            @ApiParam(value = "手机号", required = true) @RequestParam String mobile,
            @ApiParam(value = "验证码", required = true) @RequestParam String code) {
        String key = "sms:code:" + mobile;
        Object cacheCode = redisTemplate.opsForValue().get(key);
        if (cacheCode != null && cacheCode.toString().equals(code)) {
            redisTemplate.delete(key);
            return new PccAjaxResult(200, "验证码正确", true);
        }
        return new PccAjaxResult(200, "验证码错误", false);
    }
}