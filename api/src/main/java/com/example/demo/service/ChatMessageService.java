package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.ChatMessage;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessage> {

    List<ChatMessage> listBySessionId(Long sessionId);

    ChatMessage saveMessage(ChatMessage message);
}
