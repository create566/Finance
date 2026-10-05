package com.kzip.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kzip.app.entity.ModelConfig;

import java.util.List;
import java.util.Map;

public interface ModelConfigService extends IService<ModelConfig> {

    /**
     * 管理端列表查询。
     * @param query   结构化过滤条件（name/modelType/provider）
     * @param keyword 模糊搜索词，同时匹配模型名称与模型标识，可为空
     */
    List<ModelConfig> listAll(ModelConfig query, String keyword);

    boolean saveWithCheck(ModelConfig config);

    boolean updateWithCheck(ModelConfig config);

    boolean removeConfig(Long id);

    Map<String, Object> testConnect(Long id);
}