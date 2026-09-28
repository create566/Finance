package com.kzip.app.controller;

import com.kzip.app.service.ChatMessageService;
import com.kzip.app.service.ReActService;
import com.kzip.app.service.SessionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

// 跨域统一由 CorsConfig 处理，避免与 @CrossOrigin("*") 重复配置冲突
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ReActService reActService;
    private final SessionService sessionService;
    private final ChatMessageService chatMessageService;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam(defaultValue = "讲个笑话") String prompt,
                              @RequestParam(defaultValue = "") String chatId) {
        return reActService.chatStream(prompt,
                chatId != null && !chatId.isEmpty() ? chatId : "chat-" + System.currentTimeMillis());
    }

    @PostMapping("/chat-sync")
    public Result<Map<String, Object>> chatSync(@RequestBody Map<String, Object> body) {
        String question = (body.get("question") != null) ? body.get("question").toString() : "";
        Object sidObj = body.get("sessionId");
        Long sessionId = (sidObj != null && !sidObj.toString().isEmpty())
                ? Long.valueOf(sidObj.toString()) : null;
        Object uidObj = body.get("userId");
        Long userId = (uidObj != null && !uidObj.toString().isEmpty())
                ? Long.valueOf(uidObj.toString()) : 1L;

        // 数字解析失败由 GlobalExceptionHandler 统一转 Result.fail，无需在此 try/catch
        if (question.trim().isEmpty()) {
            return Result.fail("问题不能为空");
        }

        sessionId = ensureSession(sessionId, userId);
        sessionService.saveUserMessage(sessionId, userId, question);

        String chatId = "session-" + sessionId;
        String answer = reActService.chat(question, chatId);

        sessionService.saveAssistantMessage(sessionId, userId, answer, null);

        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);
        result.put("sessionId", sessionId);
        log.info("AI问答完成: sessionId={}, question={}", sessionId,
                question.substring(0, Math.min(30, question.length())));
        return Result.success(result);
    }

    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String prompt,
                                    @RequestParam(defaultValue = "") String chatId,
                                    @RequestParam(defaultValue = "1") Long userId,
                                    @RequestParam(required = false) Long sessionId,
                                    HttpServletResponse response) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return Flux.just("问题不能为空");
        }

        sessionId = ensureSession(sessionId, userId);
        final Long finalSessionId = sessionId;

        // 记忆 conversationId 与 sessionId 对齐：即使前端不回传 chatId，
        // 只要带 sessionId 请求，多轮上下文就能稳定命中同一份记忆
        String effectiveChatId = (chatId != null && !chatId.isEmpty())
                ? chatId : "session-" + finalSessionId;

        sessionService.saveUserMessage(finalSessionId, userId, prompt);
        response.setHeader("X-Session-Id", String.valueOf(finalSessionId));
        response.setHeader("Access-Control-Expose-Headers", "X-Session-Id");

        StringBuilder fullAnswer = new StringBuilder();

        return reActService.chatStream(prompt, effectiveChatId)
                .doOnNext(fullAnswer::append)
                // doFinally 覆盖正常完成/取消/异常所有信号：
                // 正常完成才落库；客户端中断（cancel）不落半截答案，避免脏数据
                .doFinally(signal -> {
                    if (signal == SignalType.ON_COMPLETE && fullAnswer.length() > 0) {
                        sessionService.saveAssistantMessage(finalSessionId, userId,
                                fullAnswer.toString(), null);
                        log.info("SSE流式完成+落库: chatId={}, sessionId={}, length={}",
                                effectiveChatId, finalSessionId, fullAnswer.length());
                    } else if (signal == SignalType.ON_ERROR) {
                        log.error("SSE流式异常: chatId={}, sessionId={}, 已接收长度={}",
                                effectiveChatId, finalSessionId, fullAnswer.length());
                    } else if (signal == SignalType.CANCEL) {
                        log.warn("SSE客户端中断: chatId={}, sessionId={}",
                                effectiveChatId, finalSessionId);
                    }
                });
    }

    private Long ensureSession(Long sessionId, Long userId) {
        if (sessionId != null) {
            return sessionId;
        }
        Long created = sessionService.createSession(userId, "", "", "");
        if (created == null) {
            // 理论上不可达（createSession 失败会抛异常），防御性兜底
            throw new IllegalStateException("会话创建失败");
        }
        return created;
    }

    @GetMapping("/chat-history/{sessionId}")
    public Map<String, Object> chatHistory(@PathVariable Long sessionId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<?> messages = chatMessageService.listBySessionId(sessionId);
            result.put("sessionId", sessionId);
            result.put("messages", messages);
            result.put("total", messages != null ? messages.size() : 0);
            return result;
        } catch (Exception e) {
            log.error("获取聊天历史失败: sessionId={}, error={}", sessionId, e.getMessage());
            result.put("error", "获取聊天历史失败");
            return result;
        }
    }
}