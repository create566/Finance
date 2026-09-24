package com.kzip.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kzip.app.entity.ModelConfig;

import java.util.List;

public interface ModelConfigService extends IService<ModelConfig> {

    List<ModelConfig> listAll(ModelConfig query);

    boolean saveWithCheck(ModelConfig config);

    boolean updateWithCheck(ModelConfig config);

    boolean removeConfig(Long id);
}