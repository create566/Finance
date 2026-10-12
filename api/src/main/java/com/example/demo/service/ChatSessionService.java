package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.ChatSession;

public interface ChatSessionService extends IService<ChatSession> {

    IPage<ChatSession> pageList(Integer pageNum, Integer pageSize, String keyword, Integer status);

    IPage<ChatSession> pageByUserId(Long userId, Integer pageNum, Integer pageSize);

    ChatSession createSession(ChatSession session);

    boolean closeSession(Long id);
}