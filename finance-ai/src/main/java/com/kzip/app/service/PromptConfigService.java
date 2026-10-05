package com.kzip.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kzip.app.entity.PromptConfig;

import java.util.List;

public interface PromptConfigService extends IService<PromptConfig> {

    /**
     * 管理端列表查询。
     * @param query   结构化过滤条件（promptKey/name/scene/contentType）
     * @param keyword 模糊搜索词，同时匹配指令名称与指令标识，可为空
     */
    List<PromptConfig> listAll(PromptConfig query, String keyword);

    List<PromptConfig> listActiveByScene(String scene);

    PromptConfig getActiveByScene(String scene);

    boolean saveWithCheck(PromptConfig config);

    boolean updateWithCheck(PromptConfig config);

    boolean toggleStatus(Long id);

    boolean removeConfig(Long id);
}