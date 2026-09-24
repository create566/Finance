package com.kzip.app.controller;

import com.kzip.app.repository.ChatHistoryRepository;
import com.kzip.app.service.PromptService;
import com.kzip.app.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import reactor.core.publisher.Flux;



@CrossOrigin("*")
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatClient chatClient;

    private final ChatClient knowledgeChatClient;

    private final ChatMemory chatMemory;

    private final ChatHistoryRepository chatHistoryRepository;

    private final PromptService promptService;

    private final SessionService sessionService;

    @RequestMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam(defaultValue = "讲个笑话") String prompt, String chatId) {
        return chatClient
                .prompt(prompt)
                .stream()
                .content();
    }

    @PostMapping("/chat-sync")
    public Map<String, Object> chatSync(@RequestBody Map<String, Object> body) {
        String question = (body.get("question") != null) ? body.get("question").toString() : "";
        Object sidObj = body.get("sessionId");
        Long sessionId = (sidObj != null && !sidObj.toString().isEmpty())
                ? Long.valueOf(sidObj.toString()) : null;
        Object uidObj = body.get("userId");
        Long userId = (uidObj != null && !uidObj.toString().isEmpty())
                ? Long.valueOf(uidObj.toString()) : 1L;

        if (question.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("answer", "问题不能为空");
            err.put("sessionId", sessionId);
            return err;
        }

        if (sessionId == null) {
            sessionId = sessionService.createSession(userId, "", "", "");
        }

        sessionService.saveUserMessage(sessionId, userId, question);

        String systemPrompt = promptService.getSystemPromptByScene("chat");
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            systemPrompt = "你是旺旺信贷的智能客服助手，请用专业、友好的的态度回答用户的金融相关问题。";
        }
        final String finalPrompt = systemPrompt;
        String answer = chatClient.prompt()
                .system(s -> s.text(finalPrompt))
                .user(question)
                .call()
                .content();

        sessionService.saveAssistantMessage(sessionId, userId, answer, null);

        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);
        result.put("sessionId", sessionId);
        log.info("AI问答完成: sessionId={}, question={}", sessionId,
                question.substring(0, Math.min(30, question.length())));
        return result;
    }

    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String prompt,
                                    @RequestParam(defaultValue = "") String chatId,
                                    @RequestParam(defaultValue = "1") Long userId) {
        String effectiveChatId = chatId.isEmpty() ? "chat-" + System.currentTimeMillis() : chatId;

        String systemPrompt = promptService.getSystemPromptByScene("chat");
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            systemPrompt = "你是旺旺信贷的智能客服助手，请用专业、友好的态度回答用户的金融相关问题。";
        }

        String finalSystemPrompt = systemPrompt;
        String finalChatId = effectiveChatId;

        return chatClient.prompt()
                .system(s -> s.text(finalSystemPrompt))
                .user(prompt)
                .advisors(a -> a.param("chat_memory_conversation_id", finalChatId))
                .stream()
                .content()
                .doOnComplete(() -> {
                    log.info("SSE流式对话完成: chatId={}", finalChatId);
                })
                .doOnError(e -> {
                    log.error("SSE流式对话异常: chatId={}, error={}", finalChatId, e.getMessage());
                });
    }

    // ==================== 知识库 RAG 聊天接口 ====================

    @PostMapping("/knowledge-chat")
    public Map<String, Object> knowledgeChat(@RequestBody Map<String, Object> body) {
        String question = (body.get("question") != null) ? body.get("question").toString() : "";
        Object kbIdObj = body.get("knowledgeBaseId");
        Long knowledgeBaseId = (kbIdObj != null && !kbIdObj.toString().isEmpty())
                ? Long.valueOf(kbIdObj.toString()) : null;
        Object sidObj = body.get("sessionId");
        Long sessionId = (sidObj != null && !sidObj.toString().isEmpty())
                ? Long.valueOf(sidObj.toString()) : null;
        Object uidObj = body.get("userId");
        Long userId = (uidObj != null && !uidObj.toString().isEmpty())
                ? Long.valueOf(uidObj.toString()) : 1L;

        if (question.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("answer", "问题不能为空");
            err.put("sessionId", sessionId);
            return err;
        }

        if (sessionId == null) {
            sessionId = sessionService.createSession(userId, "", "", "");
        }

        sessionService.saveUserMessage(sessionId, userId, question);

        String answer;
        if (knowledgeBaseId != null) {
            String filter = "knowledgeBaseId == '" + knowledgeBaseId + "'";
            log.info("知识库RAG问答: sessionId={}, knowledgeBaseId={}, filter={}", sessionId, knowledgeBaseId, filter);
            answer = knowledgeChatClient.prompt()
                    .user(question)
                    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filter))
                    .call()
                    .content();
        } else {
            String systemPrompt = promptService.getSystemPromptByScene("chat");
            if (systemPrompt == null || systemPrompt.isEmpty()) {
                systemPrompt = "你是旺旺信贷的智能客服助手，请用专业、友好的态度回答用户的金融相关问题。";
            }
            final String finalPrompt = systemPrompt;
            answer = chatClient.prompt()
                    .system(s -> s.text(finalPrompt))
                    .user(question)
                    .call()
                    .content();
        }

        sessionService.saveAssistantMessage(sessionId, userId, answer, null);

        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);
        result.put("sessionId", sessionId);
        return result;
    }

    @GetMapping(value = "/knowledge-chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> knowledgeChatStream(@RequestParam String prompt,
                                             @RequestParam(required = false) Long knowledgeBaseId,
                                             @RequestParam(defaultValue = "") String chatId,
                                             @RequestParam(defaultValue = "1") Long userId) {
        String effectiveChatId = chatId.isEmpty() ? "chat-" + System.currentTimeMillis() : chatId;

        if (knowledgeBaseId != null) {
            String filter = "knowledgeBaseId == '" + knowledgeBaseId + "'";
            log.info("知识库RAG流式问答: chatId={}, knowledgeBaseId={}, filter={}", effectiveChatId, knowledgeBaseId, filter);
            return knowledgeChatClient.prompt()
                    .user(prompt)
                    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filter))
                    .advisors(a -> a.param("chat_memory_conversation_id", effectiveChatId))
                    .stream()
                    .content()
                    .doOnComplete(() -> log.info("知识库RAG流式对话完成: chatId={}", effectiveChatId))
                    .doOnError(e -> log.error("知识库RAG流式对话异常: chatId={}, error={}", effectiveChatId, e.getMessage()));
        }

        String systemPrompt = promptService.getSystemPromptByScene("chat");
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            systemPrompt = "你是旺旺信贷的智能客服助手，请用专业、友好的态度回答用户的金融相关问题。";
        }
        String finalSystemPrompt = systemPrompt;
        String finalChatId = effectiveChatId;

        return chatClient.prompt()
                .system(s -> s.text(finalSystemPrompt))
                .user(prompt)
                .advisors(a -> a.param("chat_memory_conversation_id", finalChatId))
                .stream()
                .content()
                .doOnComplete(() -> log.info("SSE流式对话完成: chatId={}", finalChatId))
                .doOnError(e -> log.error("SSE流式对话异常: chatId={}, error={}", finalChatId, e.getMessage()));
    }

}