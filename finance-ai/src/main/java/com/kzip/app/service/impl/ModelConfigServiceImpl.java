package com.kzip.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kzip.app.entity.ModelConfig;
import com.kzip.app.mapper.ModelConfigMapper;
import com.kzip.app.service.ModelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ModelConfigServiceImpl extends ServiceImpl<ModelConfigMapper, ModelConfig> implements ModelConfigService {

    @Override
    public List<ModelConfig> listAll(ModelConfig query) {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.hasText(query.getName())) {
                wrapper.like(ModelConfig::getName, query.getName().trim());
            }
            if (StringUtils.hasText(query.getModelType())) {
                wrapper.eq(ModelConfig::getModelType, query.getModelType());
            }
            if (StringUtils.hasText(query.getProvider())) {
                wrapper.eq(ModelConfig::getProvider, query.getProvider());
            }
        }
        wrapper.orderByDesc(ModelConfig::getUpdateTime);
        return this.list(wrapper);
    }

    @Override
    @Transactional
    public boolean saveWithCheck(ModelConfig config) {
        if (!StringUtils.hasText(config.getName())) {
            throw new RuntimeException("模型名称不能为空");
        }
        if (!StringUtils.hasText(config.getModelType())) {
            throw new RuntimeException("模型类型不能为空");
        }
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            this.baseMapper.clearDefaultByType(config.getModelType());
        }
        config.setStatus(config.getStatus() != null ? config.getStatus() : 1);
        return this.save(config);
    }

    @Override
    @Transactional
    public boolean updateWithCheck(ModelConfig config) {
        if (config.getId() == null) {
            throw new RuntimeException("ID不能为空");
        }
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            this.baseMapper.clearDefaultByType(config.getModelType());
        }
        return this.updateById(config);
    }

    @Override
    public boolean removeConfig(Long id) {
        return this.removeById(id);
    }

    @Override
    public Map<String, Object> testConnect(Long id) {
        Map<String, Object> result = new HashMap<>();
        ModelConfig config = this.getById(id);
        if (config == null) {
            result.put("success", false);
            result.put("latency", 0);
            result.put("message", "模型配置不存在");
            return result;
        }
        if (!StringUtils.hasText(config.getApiUrl())) {
            result.put("success", false);
            result.put("latency", 0);
            result.put("message", "API地址未配置");
            return result;
        }
        long start = System.currentTimeMillis();
        try {
            java.net.URL url = new java.net.URL(config.getApiUrl());
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("HEAD");
            int code = conn.getResponseCode();
            conn.disconnect();
            long latency = System.currentTimeMillis() - start;
            if (code >= 200 && code < 500) {
                result.put("success", true);
                result.put("latency", latency);
                result.put("message", "连接成功");
            } else {
                result.put("success", false);
                result.put("latency", latency);
                result.put("message", "HTTP " + code);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            result.put("success", false);
            result.put("latency", latency);
            result.put("message", e.getMessage());
        }
        return result;
    }
}