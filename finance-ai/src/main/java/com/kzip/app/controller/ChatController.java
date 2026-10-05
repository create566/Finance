package com.kzip.app.controller;

import com.kzip.app.config.UserContext;
import com.kzip.app.mongodb.MongoSessionHistoryService;
import com.kzip.app.service.ContentGuard;
import com.kzip.app.service.ReActService;
import com.kzip.app.service.SessionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.kzip.app.tool.KnowledgeTools;

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
    private final MongoSessionHistoryService mongoService;
    private final ChatMemory chatMemory;
    private final ContentGuard contentGuard;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam(defaultValue = "讲个笑话") String prompt,
                              @RequestParam(defaultValue = "") String chatId) {
        ContentGuard.Verdict verdict = contentGuard.check(prompt);
        if (contentGuard.isBlocked(verdict)) {
            return Flux.just(contentGuard.replyFor(verdict));
        }
        return reActService.chatStream(prompt,
                chatId != null && !chatId.isEmpty() ? chatId : "chat-" + System.currentTimeMillis());
    }

    @PostMapping("/chat-sync")
    public Result<Map<String, Object>> chatSync(@RequestBody Map<String, Object> body) {
        String question = (body.get("question") != null) ? body.get("question").toString() : "";
        Object sidObj = body.get("sessionId");
        Long sessionId = (sidObj != null && !sidObj.toString().isEmpty())
                ? Long.valueOf(sidObj.toString()) : null;
        // userId 一律取登录态（UserAuthInterceptor 写入），不再信任前端自报，
        // 修复此前 userId=1 写死导致所有用户共享会话与记忆的问题
        Long userId = UserContext.getUserid();
        // 前端选择的知识库 id（可选）：透传给检索工具做过滤
        Long kbId = parseKbId(body.get("knowledgeBaseId"));

        if (question.trim().isEmpty()) {
            return Result.fail("问题不能为空");
        }

        sessionId = ensureSession(sessionId, userId);
        KnowledgeTools.setCurrentKbId(kbId);
        try {
            sessionService.saveUserMessage(sessionId, userId, question);

            // 规则前置：命中违规词/转人工词直接用固定话术回复，不走大模型
            ContentGuard.Verdict verdict = contentGuard.check(question);
            if (contentGuard.isBlocked(verdict)) {
                String reply = contentGuard.replyFor(verdict);
                sessionService.saveAssistantMessage(sessionId, userId, reply, null);
                Map<String, Object> guarded = new HashMap<>();
                guarded.put("answer", reply);
                guarded.put("sessionId", sessionId);
                guarded.put("guard", contentGuard.tagOf(verdict));
                log.info("规则命中，未调用大模型: sessionId={}, verdict={}", sessionId, verdict);
                return Result.success(guarded);
            }

            String chatId = "session-" + sessionId;
            String answer = reActService.chat(question, chatId);
            if (answer == null) {
                answer = "";   // 空值兜底：模型可能返回 null，避免 null 落库
            }

            sessionService.saveAssistantMessage(sessionId, userId, answer, null);

            Map<String, Object> result = new HashMap<>();
            result.put("answer", answer);
            result.put("sessionId", sessionId);
            log.info("AI问答完成: sessionId={}, userId={}, question={}", sessionId, userId,
                    question.substring(0, Math.min(30, question.length())));
            return Result.success(result);
        } finally {
            KnowledgeTools.resetToolCalled();
            KnowledgeTools.clearKbId();
        }
    }

    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String prompt,
                                    @RequestParam(defaultValue = "") String chatId,
                                    @RequestParam(required = false) Long sessionId,
                                    @RequestParam(required = false) Long knowledgeBaseId,
                                    HttpServletResponse response) {
        Long userId = UserContext.getUserid();
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
        response.setHeader("Access-Control-Expose-Headers", "X-Session-Id, X-Guard");

        // 规则前置（与 /chat-sync 一致）：命中违规词/转人工词直接返回固定话术，
        // 既不检索知识库也不调用大模型。放在 setCurrentKbId 之前，避免污染 ThreadLocal。
        ContentGuard.Verdict verdict = contentGuard.check(prompt);
        if (contentGuard.isBlocked(verdict)) {
            String reply = contentGuard.replyFor(verdict);
            sessionService.saveAssistantMessage(finalSessionId, userId, reply, null);
            // SSE 是纯 token 流，标记走响应头而不是拼进 token（拼进去会污染气泡内容）
            response.setHeader("X-Guard", contentGuard.tagOf(verdict));
            log.info("SSE 规则命中，未调用大模型: sessionId={}, verdict={}", finalSessionId, verdict);
            return Flux.just(reply);
        }

        KnowledgeTools.setCurrentKbId(knowledgeBaseId);

        StringBuilder fullAnswer = new StringBuilder();

        return reActService.chatStream(prompt, effectiveChatId)
                .doOnNext(fullAnswer::append)
                // doFinally 覆盖正常完成/取消/异常所有信号：
                // 正常完成才落库；客户端中断（cancel）不落半截答案，避免脏数据
                .doFinally(signal -> {
                    KnowledgeTools.resetToolCalled();
                    KnowledgeTools.clearKbId();
                    if (signal == SignalType.ON_COMPLETE && fullAnswer.length() > 0) {
                        sessionService.saveAssistantMessage(finalSessionId, userId,
                                fullAnswer.toString(), null);
                        log.info("SSE流式完成+落库: chatId={}, sessionId={}, userId={}, length={}",
                                effectiveChatId, finalSessionId, userId, fullAnswer.length());
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
        // 会话可能已被删除（前端 localStorage 仍持有旧 sessionId），
        // 此时复用会导致消息写入不存在的会话、记忆也串到已删会话上，故校验存在性后重建
        if (sessionId != null && mongoService.sessionExists(sessionId)) {
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
            // 会话消息已迁移到 MongoDB，从 Mongo 读取；仅会话属主可查看
            if (!ownsSession(sessionId, UserContext.getUserid())) {
                result.put("sessionId", sessionId);
                result.put("messages", List.of());
                result.put("total", 0);
                result.put("error", "会话不存在或无权查看");
                return result;
            }
            Map<String, Object> detail = mongoService.detail(sessionId);
            if (detail == null) {
                result.put("sessionId", sessionId);
                result.put("messages", List.of());
                result.put("total", 0);
                return result;
            }
            List<?> messages = (List<?>) detail.get("messages");
            result.put("sessionId", sessionId);
            result.put("messages", messages);
            result.put("total", messages != null ? messages.size() : 0);
            result.put("session", detail.get("session"));
            return result;
        } catch (Exception e) {
            log.error("获取聊天历史失败: sessionId={}, error={}", sessionId, e.getMessage());
            result.put("error", "获取聊天历史失败");
            return result;
        }
    }

    /**
     * 当前用户的历史会话列表（按 updateTime 倒序）。
     * 前端"历史会话"侧边栏调用；点击某会话后用 /chat-history/{id} 拉取消息，
     * 并以该 sessionId 继续提问，后端即从 chat_memory 恢复上下文记忆。
     * userId 一律取登录态，前端传参不再生效（防止越权查看他人会话）。
     */
    @GetMapping("/sessions")
    public Result<Map<String, Object>> mySessions(@RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = UserContext.getUserid();
        var page = sessionService.listSessions(userId, pageNum, pageSize);
        Map<String, Object> data = new HashMap<>();
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        return Result.success(data);
    }

    /**
     * 删除当前用户的一个历史会话（连同消息、LLM上下文记忆一起移除）。
     * 前台"历史会话"侧边栏删除入口调用。
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Object> deleteSession(@PathVariable Long sessionId) {
        if (sessionId == null) {
            return Result.fail("会话ID不能为空");
        }
        Long userId = UserContext.getUserid();
        if (!ownsSession(sessionId, userId)) {
            return Result.fail("无权删除该会话");
        }
        mongoService.deleteSession(sessionId);
        // 同步清除 LLM 上下文记忆（chat_memory 表）：会话记忆 key 为 "session-{id}"，
        // 意图路由阶段还有独立 key "session-{id}:intent"。只删 Mongo 不删记忆的话，
        // 记忆表会越积越多，且用户感知"删了却还记着"
        try {
            chatMemory.clear("session-" + sessionId);
            chatMemory.clear("session-" + sessionId + ":intent");
        } catch (Exception e) {
            log.warn("清除会话上下文记忆失败(不影响会话删除): sessionId={}, error={}", sessionId, e.getMessage());
        }
        return Result.success("删除成功", null);
    }

    /** 校验会话归属：sessionId 必须属于当前登录用户 */
    private boolean ownsSession(Long sessionId, Long userId) {
        try {
            Map<String, Object> detail = mongoService.detail(sessionId);
            if (detail == null) {
                return false;
            }
            Object sessionObj = detail.get("session");
            Object owner = null;
            if (sessionObj instanceof Map<?, ?> m) {
                owner = m.get("userId");
            } else if (sessionObj instanceof com.kzip.app.mongodb.ChatSessionDoc doc) {
                owner = doc.getUserId();
            }
            return owner instanceof Number n && n.longValue() == userId;
        } catch (Exception e) {
            log.warn("会话归属校验失败: sessionId={}, error={}", sessionId, e.getMessage());
            return false;
        }
    }

    /** 解析前端传入的知识库 id（可选） */
    private Long parseKbId(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            long v = Long.parseLong(obj.toString());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}