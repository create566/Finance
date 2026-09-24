package com.kzip.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kzip.app.entity.PromptConfig;

import java.util.List;

public interface PromptConfigService extends IService<PromptConfig> {

    List<PromptConfig> listAll(PromptConfig query);

    List<PromptConfig> listActiveByScene(String scene);

    PromptConfig getActiveByScene(String scene);

    boolean saveWithCheck(PromptConfig config);

    boolean updateWithCheck(PromptConfig config);

    boolean toggleStatus(Long id);

    boolean removeConfig(Long id);
}