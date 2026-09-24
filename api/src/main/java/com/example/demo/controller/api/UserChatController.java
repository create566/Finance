package com.example.demo.controller.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.ChatSession;
import com.example.demo.service.ChatMessageService;
import com.example.demo.service.ChatSessionService;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "用户端客服对话")
@RestController
@RequestMapping("/api/core/chat")
public class UserChatController {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @ApiOperation("获取我的会话列表")
    @GetMapping("/session/list")
    public PccAjaxResult mySessions(@RequestParam Long userId,
                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                     @RequestParam(defaultValue = "20") Integer pageSize) {
        IPage<ChatSession> result = chatSessionService.pageByUserId(userId, pageNum, pageSize);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "查询成功", data);
    }

    @ApiOperation("创建新会话")
    @PostMapping("/session/create")
    public PccAjaxResult createSession(@RequestBody ChatSession session) {
        if (session.getUserId() == null) {
            return new PccAjaxResult(400, "用户ID不能为空", null);
        }
        ChatSession created = chatSessionService.createSession(session);
        return new PccAjaxResult(200, "创建成功", created);
    }

    @ApiOperation("获取某个会话的消息列表")
    @GetMapping("/message/list")
    public PccAjaxResult messageList(@RequestParam Long sessionId) {
        List<ChatMessage> messages = chatMessageService.listBySessionId(sessionId);
        return new PccAjaxResult(200, "查询成功", messages);
    }

    @ApiOperation("发送消息（保存用户提问，返回消息ID）")
    @PostMapping("/message/send")
    public PccAjaxResult sendMessage(@RequestBody ChatMessage message) {
        try {
            ChatMessage saved = chatMessageService.saveMessage(message);
            return new PccAjaxResult(200, "发送成功", saved);
        } catch (RuntimeException e) {
            return new PccAjaxResult(400, e.getMessage(), null);
        }
    }

    @ApiOperation("保存AI回复消息")
    @PostMapping("/message/reply")
    public PccAjaxResult saveReply(@RequestBody ChatMessage message) {
        try {
            ChatMessage saved = chatMessageService.saveMessage(message);
            return new PccAjaxResult(200, "保存成功", saved);
        } catch (RuntimeException e) {
            return new PccAjaxResult(400, e.getMessage(), null);
        }
    }

    @ApiOperation("结束会话")
    @PutMapping("/session/{id}/close")
    public PccAjaxResult closeSession(@PathVariable Long id) {
        boolean ok = chatSessionService.closeSession(id);
        return ok ? new PccAjaxResult(200, "操作成功", null)
                : new PccAjaxResult(500, "操作失败", null);
    }
}