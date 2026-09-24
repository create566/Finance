package com.kzip.app.controller;

import com.kzip.app.entity.ChatModelBinding;
import com.kzip.app.service.ChatModelBindingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/admin/chat/model")
public class AdminChatBindingController {

    @Autowired
    private ChatModelBindingService chatModelBindingService;

    @GetMapping("/list")
    public Result<List<ChatModelBinding>> list() {
        List<ChatModelBinding> list = chatModelBindingService.listAll();
        return Result.success("查询成功", list);
    }

    @PutMapping
    public Result<Object> update(@RequestBody ChatModelBinding binding) {
        try {
            boolean ok = chatModelBindingService.updateBinding(binding);
            return ok ? Result.success("修改成功", null)
                    : Result.error(500, "修改失败");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }
}