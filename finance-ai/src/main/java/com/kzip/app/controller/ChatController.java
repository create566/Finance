package com.kzip.app.controller;

import com.kzip.app.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;



@CrossOrigin("*")
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class ChatController {


    private final ChatClient chatClient;

    private final ChatMemory chatMemory;

    private final ChatHistoryRepository chatHistoryRepository;

    @RequestMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam(defaultValue = "讲个笑话") String prompt, String chatId) {

      //  chatHistoryRepository.save("chat",chatId);
        return chatClient
                .prompt(prompt)
             //   .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .stream()
                .content();
    }

}
