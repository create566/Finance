package com.kzip.app.controller;

import com.kzip.app.mongodb.MongoSessionHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 会话历史管理端接口（读取 MongoDB）。
 * 会话/消息记录已迁移到 MongoDB，前端契约保持不变：
 *   GET  /list            → { list, total, current, size }
 *   GET  /{id}            → { session, messages }
 *   PUT  /{id}/close
 *   DELETE /{id}
 */
@RestController
@RequestMapping("/ai/admin/chat/session")
public class AdminChatSessionController {

    private static final Logger log = LoggerFactory.getLogger(AdminChatSessionController.class);

    @Autowired
    private MongoSessionHistoryService mongoService;

    @Autowired
    private ChatMemory chatMemory;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "10") Integer pageSize,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        Map<String, Object> data = mongoService.pageSessions(pageNum, pageSize, keyword, status);
        return Result.success("查询成功", data);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> data = mongoService.detail(id);
        if (data == null) {
            return Result.error(404, "会话不存在");
        }
        return Result.success("查询成功", data);
    }

    @PutMapping("/{id}/close")
    public Result<Object> close(@PathVariable Long id) {
        boolean ok = mongoService.closeSession(id);
        return ok ? Result.success("操作成功", null)
                : Result.error(500, "操作失败");
    }

    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable Long id) {
        mongoService.deleteSession(id);
        // 与用户端删除保持一致：同步清除 chat_memory 中的上下文记忆（含意图路由的 :intent key）
        try {
            chatMemory.clear("session-" + id);
            chatMemory.clear("session-" + id + ":intent");
        } catch (Exception e) {
            log.warn("清除会话上下文记忆失败(不影响会话删除): id={}, error={}", id, e.getMessage());
        }
        return Result.success("删除成功", null);
    }
}