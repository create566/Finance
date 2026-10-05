package com.kzip.app.controller;

import com.kzip.app.entity.KnowledgeBase;
import com.kzip.app.service.KnowledgeBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面向用户端（借款用户）的公共只读知识库接口。
 *
 * <p>背景：用户端"智能客服"页面的知识库下拉需要拉取可用知识库列表，
 * 而管理端接口 /ai/admin/** 已加鉴权拦截（要求管理端 token），用户端不可能携带该 token，
 * 若直接调管理端列表会永远 401，导致下拉框空白、无法选择知识库。
 *
 * <p>方案：单独暴露一组只读接口（仅 GET 查询），映射在 /ai/knowledge/**（不在
 * AdminAuthInterceptor 的拦截前缀 /ai/admin/**、/ai/knowledge/embedding/** 内），
 * 因此对用户端开放。管理端所有增删改仍走 /ai/admin/** 受鉴权保护，安全不降级。
 */
@RestController
@RequestMapping("/ai/knowledge/base")
public class PublicKnowledgeController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /** 用户端可用的知识库列表（只读） */
    @GetMapping("/list")
    public Result<List<KnowledgeBase>> list() {
        return Result.success("查询成功", knowledgeBaseService.listAll(null, null));
    }

    /** 单个知识库详情（只读） */
    @GetMapping("/{id}")
    public Result<KnowledgeBase> get(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (kb == null) {
            return Result.error(404, "知识库不存在");
        }
        return Result.success("查询成功", kb);
    }
}
