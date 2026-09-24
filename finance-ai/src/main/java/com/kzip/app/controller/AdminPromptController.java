package com.kzip.app.controller;

import com.kzip.app.entity.PromptConfig;
import com.kzip.app.service.PromptConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/admin/chat/prompt")
public class AdminPromptController {

    @Autowired
    private PromptConfigService promptConfigService;

    @GetMapping("/list")
    public Result<List<PromptConfig>> list(PromptConfig query) {
        List<PromptConfig> list = promptConfigService.listAll(query);
        return Result.success("查询成功", list);
    }

    @GetMapping("/{id}")
    public Result<PromptConfig> getById(@PathVariable Long id) {
        PromptConfig config = promptConfigService.getById(id);
        if (config == null) {
            return Result.error(404, "提示词不存在");
        }
        return Result.success("查询成功", config);
    }

    @PostMapping
    public Result<Long> add(@RequestBody PromptConfig config) {
        try {
            boolean ok = promptConfigService.saveWithCheck(config);
            return ok ? Result.success("新增成功", config.getId())
                    : Result.error(500, "新增失败");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping
    public Result<Object> update(@RequestBody PromptConfig config) {
        try {
            boolean ok = promptConfigService.updateWithCheck(config);
            return ok ? Result.success("修改成功", null)
                    : Result.error(500, "修改失败");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable Long id) {
        boolean ok = promptConfigService.removeConfig(id);
        return ok ? Result.success("删除成功", null)
                : Result.error(500, "删除失败");
    }

    @PutMapping("/toggle/{id}")
    public Result<Object> toggle(@PathVariable Long id) {
        try {
            boolean ok = promptConfigService.toggleStatus(id);
            return ok ? Result.success("操作成功", null)
                    : Result.error(500, "操作失败");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }
}