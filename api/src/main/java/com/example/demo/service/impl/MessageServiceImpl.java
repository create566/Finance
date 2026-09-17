package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.client.SMSApiSmsClient;
import com.example.demo.entity.Message;
import com.example.demo.entity.User;
import com.example.demo.enums.MessageTypeEnum;
import com.example.demo.mapper.MessageMapper;
import com.example.demo.service.MessageService;
import com.example.demo.service.UserService;
import com.wwfinance.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 消息 Service 实现
 * 双通道：站内信落库（必达） + 短信微服务（与验证码同通道，失败容错）
 */
@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    @Autowired
    private SMSApiSmsClient smsApiSmsClient;

    @Autowired
    private UserService userService;

    @Override
    public Message send(Long userId, MessageTypeEnum type, String title, String content) {
        // 1. 站内信落库（消息必达，不依赖外部服务）
        Message message = new Message();
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);
        message.setMessageType(type.getCode());
        message.setReadFlag(0);
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        this.save(message);

        // 2. 走短信通道（与发送验证码同一 Feign 客户端，失败由 fallback 兜底，不抛异常）
        try {
            User user = userService.getById(userId);
            if (user != null && user.getMobile() != null) {
                smsApiSmsClient.sendNotify(user.getMobile(), content);
            }
        } catch (Exception e) {
            log.warn("短信通知发送异常，站内信已落库 userId={}, msg={}", userId, e.getMessage());
        }
        return message;
    }

    @Override
    public Message sendOverdueNotice(Long userId, String content) {
        return send(userId, MessageTypeEnum.OVERDUE, "【逾期催收通知】", content);
    }

    @Override
    public Page<Message> pageByUser(Long userId, Integer pageNum, Integer pageSize) {
        return this.page(new Page<>(pageNum, pageSize),
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
