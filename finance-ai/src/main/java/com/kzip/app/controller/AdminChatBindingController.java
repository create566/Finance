package com.kzip.app.controller;

import com.kzip.app.entity.ChatModelBinding;
import com.kzip.app.service.ChatModelBindingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端「对话模型选择」：为智能客服的每个对话场景绑定 模型 / 提示词 / 知识库。
 *
 * <p>scene 与运行时读取的场景一一对应（intent 意图路由 / chat 闲聊应答 / rag 知识库问答）。
 * 绑定既是"覆盖层"：运行时会优先取这里的模型与提示词，未绑定/未启用才回退 Prompt 设置的场景默认值。
 */
@RestController
@RequestMapping("/ai/admin/chat/model")
public class AdminChatBindingController {

    @Autowired
    private ChatModelBindingService chatModelBindingService;

    @GetMapping("/list")
    public Result<List<ChatModelBinding>> list() {
        return Result.success("查询成功", chatModelBindingService.listAll());
    }

    /** 可选处理环节 + 是否已配置，供"新增绑定"下拉使用 */
    @GetMapping("/scenes")
    public Result<List<Map<String, Object>>> scenes() {
        Set<String> bound = new HashSet<>();
        for (ChatModelBinding binding : chatModelBindingService.listAll()) {
            bound.add(binding.getScene());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        chatModelBindingService.supportedScenes().forEach((scene, sceneName) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("scene", scene);
            item.put("sceneName", sceneName);
            item.put("bound", bound.contains(scene));
            list.add(item);
        });
        return Result.success("查询成功", list);
    }

    /** 各处理环节"当前实际生效"的模型及来源（绑定 → 场景 Prompt → 代码内置默认） */
    @GetMapping("/effective")
    public Result<List<Map<String, Object>>> effective() {
        return Result.success("查询成功", chatModelBindingService.effectiveConfigs());
    }

    @PostMapping
    public Result<Long> add(@RequestBody ChatModelBinding binding) {
        try {
            boolean ok = chatModelBindingService.saveBinding(binding);
            return ok ? Result.success("新增成功", binding.getId())
                    : Result.error(500, "新增失败");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping
    public Result<Object> update(@RequestBody ChatModelBinding binding) {
        try {
            boolean ok = chatModelBindingService.updateBinding(binding);
            return ok ? Result.success("修改成功", null)
                    : Result.error(500, "修改失败");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/toggle/{id}")
    public Result<Object> toggle(@PathVariable Long id) {
        try {
            boolean ok = chatModelBindingService.toggleEnabled(id);
            return ok ? Result.success("操作成功", null)
                    : Result.error(500, "操作失败");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable Long id) {
        try {
            boolean ok = chatModelBindingService.removeBinding(id);
            return ok ? Result.success("删除成功", null)
                    : Result.error(500, "删除失败");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
