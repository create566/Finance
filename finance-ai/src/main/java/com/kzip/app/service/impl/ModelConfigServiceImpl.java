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

import java.util.List;

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
}