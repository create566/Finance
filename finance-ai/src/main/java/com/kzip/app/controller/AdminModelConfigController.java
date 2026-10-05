   package com.kzip.app.controller;

import com.kzip.app.entity.ModelConfig;
import com.kzip.app.service.ModelConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/admin/model/config")
public class AdminModelConfigController {

    @Autowired
    private ModelConfigService modelConfigService;

    @GetMapping("/list")
    public Result<List<ModelConfig>> list(ModelConfig query,
                                          @RequestParam(required = false) String keyword) {
        List<ModelConfig> list = modelConfigService.listAll(query, keyword);
        return Result.success("查询成功", list);
    }

    @GetMapping("/{id}")
    public Result<ModelConfig> getById(@PathVariable Long id) {
        ModelConfig config = modelConfigService.getById(id);
        if (config == null) {
            return Result.error(404, "模型配置不存在");
        }
        return Result.success("查询成功", config);
    }

    @PostMapping
    public Result<Long> add(@RequestBody ModelConfig config) {
        try {
            boolean ok = modelConfigService.saveWithCheck(config);
            return ok ? Result.success("新增成功", config.getId())
                    : Result.error(500, "新增失败");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping
    public Result<Object> update(@RequestBody ModelConfig config) {
        try {
            boolean ok = modelConfigService.updateWithCheck(config);
            return ok ? Result.success("修改成功", null)
                    : Result.error(500, "修改失败");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable Long id) {
        boolean ok = modelConfigService.removeConfig(id);
        return ok ? Result.success("删除成功", null)
                : Result.error(500, "删除失败");
    }

    @PostMapping("/test/{id}")
    public Result<Map<String, Object>> testConnect(@PathVariable Long id) {
        Map<String, Object> result = modelConfigService.testConnect(id);
        return Result.success("测试完成", result);
    }
}