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

    /** 单个手机号连续输错验证码次数上限，达到后作废当前验证码 */
    private static final int MAX_FAIL_COUNT = 5;

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

        // 频率限制：同一手机号 60 秒内不允许重复发送（防短信轰炸）
        String rateKey = "sms:rate:" + mobile;
        Boolean first = redisTemplate.opsForValue().setIfAbsent(rateKey, "1", 60, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            return new PccAjaxResult(500, "发送太频繁，请稍后再试");
        }

        // 注：不再调用 checkMobile 查重——注册接口自己会查重；
        // 绑卡等场景需要给"已注册"用户发验证码，查重会导致已注册用户永远发不了码
        String code = SmsCodeUtil.generateCode(6);
        redisTemplate.opsForValue().set("sms:code:" + mobile, code, 5, TimeUnit.MINUTES);
        // 新验证码重置失败计数
        redisTemplate.delete("sms:fail:" + mobile);

        // 教学演示：验证码不真发短信，直接打印到控制台，方便从控制台复制到前端登录
        System.out.println();
        System.out.println("================  短信验证码  ================");
        System.out.println("  手机号: " + mobile);
        System.out.println("  验证码: " + code);
        System.out.println("  有效期: 5 分钟");
        System.out.println("===============================================");
        System.out.println();
        log.info("发送验证码成功，手机号={}，验证码={}", mobile, code);
        // 安全修复：验证码只进日志（教学演示），绝不出现在 HTTP 响应体中，
        // 否则任何人可对任意手机号取码并登录他人账号
        return new PccAjaxResult(200, "验证码已发送");
    }

    @ApiOperation("校验验证码")
    @PostMapping("/checkCode")
    public PccAjaxResult checkCode(
            @ApiParam(value = "手机号", required = true) @RequestParam String mobile,
            @ApiParam(value = "验证码", required = true) @RequestParam String code) {
        String key = "sms:code:" + mobile;
        Object cacheCode = redisTemplate.opsForValue().get(key);
        if (cacheCode != null && cacheCode.toString().equals(code)) {
            // 一次性消费：验证通过即删除，防止 5 分钟有效期内被重放
            redisTemplate.delete(key);
            redisTemplate.delete("sms:fail:" + mobile);
            return new PccAjaxResult(200, "验证码正确", true);
        }
        // 失败次数限制：连续错 N 次作废验证码（防暴力破解）
        String failKey = "sms:fail:" + mobile;
        Long fails = redisTemplate.opsForValue().increment(failKey);
        redisTemplate.expire(failKey, 5, TimeUnit.MINUTES);
        if (fails != null && fails >= MAX_FAIL_COUNT && cacheCode != null) {
            redisTemplate.delete(key);
            return new PccAjaxResult(200, "错误次数过多，验证码已失效，请重新获取", false);
        }
        return new PccAjaxResult(200, "验证码错误", false);
    }

    @ApiOperation("发送通知（模拟短信）")
    @PostMapping("/sendNotify")
    public PccAjaxResult sendNotify(
            @ApiParam(value = "手机号", required = true) @RequestParam String mobile,
            @ApiParam(value = "通知内容", required = true) @RequestParam String content) {
        // 教学演示：不真发短信，打印到控制台。core 服务的满标/放款/还款/逾期通知都走这里
        System.out.println();
        System.out.println("================  模拟短信通知  ================");
        System.out.println("  手机号: " + mobile);
        System.out.println("  内容: " + content);
        System.out.println("===============================================");
        System.out.println();
        log.info("【模拟短信通知】手机号={}，内容={}", mobile, content);
        return new PccAjaxResult(200, "通知已发送");
    }
}
