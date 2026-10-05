package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.ChatSession;
import com.example.demo.mapper.ChatSessionMapper;
import com.example.demo.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.example.demo.utils.PageUtils;

@Slf4j
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    @Override
    public IPage<ChatSession> pageList(Integer pageNum, Integer pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ChatSession::getUserName, kw)
                    .or().like(ChatSession::getUserPhone, kw)
                    .or().like(ChatSession::getTitle, kw));
        }
        if (status != null) {
            wrapper.eq(ChatSession::getStatus, status);
        }
        wrapper.orderByDesc(ChatSession::getUpdateTime);
        return this.page(PageUtils.of(pageNum, pageSize), wrapper);
    }

    @Override
    public IPage<ChatSession> pageByUserId(Long userId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getUpdateTime);
        return this.page(PageUtils.of(pageNum, pageSize), wrapper);
    }

    @Override
    @Transactional
    public ChatSession createSession(ChatSession session) {
        session.setMessageCount(0);
        session.setStatus(0);
        this.save(session);
        return session;
    }

    @Override
    public boolean closeSession(Long id) {
        return this.baseMapper.closeSession(id) > 0;
    }

    @Override
    public boolean removeSession(Long id) {
        return this.removeById(id);
    }
}