package com.kzip.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kzip.app.entity.ChatMessage;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessage> {

    List<ChatMessage> listBySessionId(Long sessionId);

    ChatMessage saveMessage(ChatMessage message);

    boolean deleteBySessionId(Long sessionId);
}