package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.ChatMessage;
import com.example.demo.mapper.ChatMessageMapper;
import com.example.demo.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {

    @Override
    public List<ChatMessage> listBySessionId(Long sessionId) {
        return this.baseMapper.selectBySessionId(sessionId);
    }

    @Override
    @Transactional
    public ChatMessage saveMessage(ChatMessage message) {
        if (!StringUtils.hasText(message.getRole())) {
            throw new RuntimeException("消息角色不能为空");
        }
        if (!StringUtils.hasText(message.getContent())) {
            throw new RuntimeException("消息内容不能为空");
        }
        this.save(message);
        return message;
    }

    @Override
    public boolean deleteBySessionId(Long sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId);
        return this.remove(wrapper);
    }
}