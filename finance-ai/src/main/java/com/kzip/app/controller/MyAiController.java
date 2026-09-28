package com.kzip.app.controller;

import com.kzip.app.tool.DateTimeTools;
import com.kzip.app.tool.KnowledgeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class MyAiController {

    private final ChatClient intentClient;

    public MyAiController(ChatClient intentClient) {
        this.intentClient = intentClient;
    }

    @GetMapping("/ai-sync")
    Result<String> generationSync(@RequestParam String userInput) {
        KnowledgeTools.resetToolCalled();
        DateTimeTools.resetToolCalled();

        String content = this.intentClient.prompt()
                .user(userInput)
                .call()
                .content();

        boolean toolCalled = KnowledgeTools.wasToolCalled() || DateTimeTools.wasToolCalled();
        if (toolCalled) {
            content = "[工具已调用] " + content;
        }
        return Result.success(content);
    }

    @GetMapping(value = "/ai-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> generation(@RequestParam String userInput) {
        KnowledgeTools.resetToolCalled();
        DateTimeTools.resetToolCalled();

        return this.intentClient.prompt()
                .user(userInput)
                .stream()
                .content();
    }
}