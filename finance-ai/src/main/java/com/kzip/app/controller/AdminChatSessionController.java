package com.kzip.app.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kzip.app.entity.ChatMessage;
import com.kzip.app.entity.ChatSession;
import com.kzip.app.service.ChatMessageService;
import com.kzip.app.service.ChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/admin/chat/session")
public class AdminChatSessionController {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "10") Integer pageSize,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        IPage<ChatSession> result = chatSessionService.pageList(pageNum, pageSize, keyword, status);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return Result.success("查询成功", data);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        ChatSession session = chatSessionService.getById(id);
        if (session == null) {
            return Result.error(404, "会话不存在");
        }
        List<ChatMessage> messages = chatMessageService.listBySessionId(id);
        Map<String, Object> data = new HashMap<>();
        data.put("session", session);
        data.put("messages", messages);
        return Result.success("查询成功", data);
    }

    @PutMapping("/{id}/close")
    public Result<Object> close(@PathVariable Long id) {
        boolean ok = chatSessionService.closeSession(id);
        return ok ? Result.success("操作成功", null)
                : Result.error(500, "操作失败");
    }

    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable Long id) {
        chatMessageService.deleteBySessionId(id);
        boolean ok = chatSessionService.removeSession(id);
        return ok ? Result.success("删除成功", null)
                : Result.error(500, "删除失败");
    }
}