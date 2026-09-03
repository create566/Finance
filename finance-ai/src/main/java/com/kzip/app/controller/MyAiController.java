package com.kzip.app.controller;


import com.kzip.app.repository.ChatHistoryRepository;
import com.kzip.app.repository.LocalPdfFileRepository;
import com.kzip.app.tool.DateTimeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;


@RestController
@RequestMapping("/ai")
public class MyAiController {

    private final ChatClient chatClient;
    private final  ChatClient pdfChatClient;
    private final LocalPdfFileRepository fileRepository;

    private final ChatHistoryRepository chatRepository;



    public MyAiController(ChatClient.Builder chatClientBuilder, ChatClient pdfChatClient, LocalPdfFileRepository fileRepository, ChatHistoryRepository chatRepository) {
        // 工具调用需要同步接口才能完整执行"模型请求→调用工具→回传结果→模型生成"这一闭环
        // 流式(.stream) + 工具调用在部分模型上会被截断，因此同步接口更适合验证工具是否生效
        this.chatClient = chatClientBuilder
                .defaultTools(new DateTimeTools())
                .build();
        this.pdfChatClient = pdfChatClient;
        this.fileRepository = fileRepository;
        this.chatRepository = chatRepository;
    }

    /**
     * 同步接口：用于验证工具调用是否正常工作
     * 访问：/ai-sync?userInput=获取当前时间
     */
    @GetMapping("/ai-sync")
    Result<String> generationSync(@RequestParam String userInput) {
        String content = this.chatClient.prompt()
                .user(userInput)
                .call()
                .content();
        return Result.success(content);
    }

    /**
     * 流式接口：如果工具调用正常后再考虑切换
     */
    @GetMapping(value = "/ai-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> generation(@RequestParam String userInput) {
        return this.chatClient.prompt()
                .user(userInput)
                .stream()
                .content();
    }


    @RequestMapping(value = "/chat", produces = "text/html;charset=UTF-8")
    public Flux<String> chat(String prompt, String chatId) {
        chatRepository.save("pdf", chatId);
        Resource file = fileRepository.getFile(chatId);
        return pdfChatClient
                .prompt(prompt)
                .advisors(a -> a.param(CONVERSATION_ID, chatId))
                .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "file_name == '"+file.getFilename()+"'"))
                .stream()
                .content();
    }
}
