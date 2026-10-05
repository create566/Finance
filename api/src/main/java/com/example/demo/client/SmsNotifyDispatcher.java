package com.example.demo.client;

import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import com.wwfinance.common.result.PccAjaxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 短信通知异步派发器。
 *
 * <p>站内信由调用方在同一事务内落库（必达）；短信只是补充通道：
 * 异步执行，避免同步 HTTP 调用拖慢甚至拖垮主业务事务；
 * 最多 {@value #MAX_ATTEMPTS} 次退避重试，最终失败只记日志，绝不抛出。
 */
@Slf4j
@Component
public class SmsNotifyDispatcher {

    /** 最大尝试次数 */
    private static final int MAX_ATTEMPTS = 3;
    /** 退避基数（毫秒），第 n 次失败后等待 BACKOFF * n */
    private static final long RETRY_BACKOFF_MS = 500L;
    /** 短信服务成功码 */
    private static final int SUCCESS_CODE = 200;

    @Autowired
    private SMSApiSmsClient smsApiSmsClient;

    @Autowired
    private UserService userService;

    /**
     * 异步派发短信通知（fire-and-forget）。
     *
     * @param userId  接收用户
     * @param content 短信正文
     */
    @Async("smsExecutor")
    public void dispatch(Long userId, String content) {
        String mobile;
        try {
            User user = userService.getById(userId);
            if (user == null || !StringUtils.hasText(user.getMobile())) {
                return;
            }
            mobile = user.getMobile();
        } catch (Exception e) {
            log.warn("短信派发前置查询失败，跳过短信，userId={}，err={}", userId, e.getMessage());
            return;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                PccAjaxResult result = smsApiSmsClient.sendNotify(mobile, content);
                Object code = result == null ? null : result.get(PccAjaxResult.CODE_TAG);
                if (code instanceof Integer && (Integer) code == SUCCESS_CODE) {
                    log.info("短信通知发送成功，userId={}，第 {} 次", userId, attempt);
                    return;
                }
                log.warn("短信通知发送未成功，userId={}，第 {} 次，resp={}", userId, attempt, result);
            } catch (Exception e) {
                log.warn("短信通知发送异常，userId={}，第 {} 次，err={}", userId, attempt, e.getMessage());
            }

            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_BACKOFF_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("短信通知重试 {} 次仍失败，已降级为仅站内信，userId={}", MAX_ATTEMPTS, userId);
    }
}
