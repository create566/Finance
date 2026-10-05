package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.client.SmsNotifyDispatcher;
import com.example.demo.entity.Message;
import com.example.demo.enums.MessageTypeEnum;
import com.example.demo.mapper.MessageMapper;
import com.example.demo.service.MessageService;
import com.wwfinance.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import com.example.demo.utils.PageUtils;

/**
 * 消息 Service 实现
 * 双通道：站内信落库（同事务必达） + 短信微服务（异步派发、有界重试、失败降级）
 */
@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    @Autowired
    private SmsNotifyDispatcher smsNotifyDispatcher;

    @Override
    public Message send(Long userId, MessageTypeEnum type, String title, String content) {
        // 1. 站内信落库（消息必达，与主业务同一事务）
        Message message = new Message();
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);
        message.setMessageType(type.getCode());
        message.setReadFlag(0);
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        this.save(message);

        // 2. 短信通道异步派发：不阻塞主业务事务；重试与失败降级在派发器内部完成
        smsNotifyDispatcher.dispatch(userId, content);
        return message;
    }

    @Override
    public Message sendOverdueNotice(Long userId, String content) {
        return send(userId, MessageTypeEnum.OVERDUE, "【逾期催收通知】", content);
    }

    @Override
    public Page<Message> pageByUser(Long userId, Integer pageNum, Integer pageSize) {
        return this.page(PageUtils.of(pageNum, pageSize),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId)
                        .orderByDesc(Message::getCreateTime));
    }

    @Override
    public long countUnread(Long userId) {
        return this.count(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getReadFlag, 0));
    }

    @Override
    public boolean markRead(Long messageId, Long userId) {
        Message message = this.getById(messageId);
        if (message == null || !message.getUserId().equals(userId)) {
            throw new ResourceNotFoundException(500, "消息不存在");
        }
        return this.update(new LambdaUpdateWrapper<Message>()
                .eq(Message::getId, messageId)
                .set(Message::getReadFlag, 1)
                .set(Message::getUpdateTime, LocalDateTime.now()));
    }
}
