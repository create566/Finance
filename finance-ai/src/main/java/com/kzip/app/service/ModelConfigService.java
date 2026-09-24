package com.kzip.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kzip.app.entity.ModelConfig;

import java.util.List;
import java.util.Map;

public interface ModelConfigService extends IService<ModelConfig> {

    List<ModelConfig> listAll(ModelConfig query);

    boolean saveWithCheck(ModelConfig config);

    boolean updateWithCheck(ModelConfig config);

    boolean removeConfig(Long id);

    Map<String, Object> testConnect(Long id);
}