package com.kzip.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kzip.app.entity.PromptConfig;
import com.kzip.app.mapper.PromptConfigMapper;
import com.kzip.app.service.PromptConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class PromptConfigServiceImpl extends ServiceImpl<PromptConfigMapper, PromptConfig> implements PromptConfigService {

    @Override
    public List<PromptConfig> listAll(PromptConfig query) {
        LambdaQueryWrapper<PromptConfig> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.hasText(query.getPromptKey())) {
                wrapper.like(PromptConfig::getPromptKey, query.getPromptKey().trim());
            }
            if (StringUtils.hasText(query.getName())) {
                wrapper.like(PromptConfig::getName, query.getName().trim());
            }
            if (StringUtils.hasText(query.getScene())) {
                wrapper.eq(PromptConfig::getScene, query.getScene());
            }
            if (StringUtils.hasText(query.getContentType())) {
                wrapper.eq(PromptConfig::getContentType, query.getContentType());
            }
        }
        wrapper.orderByDesc(PromptConfig::getUpdateTime);
        return this.list(wrapper);
    }

    @Override
    public List<PromptConfig> listActiveByScene(String scene) {
        return this.baseMapper.selectActiveByScene(scene);
    }

    @Override
    public PromptConfig getActiveByScene(String scene) {
        List<PromptConfig> list = this.baseMapper.selectActiveByScene(scene);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    @Transactional
    public boolean saveWithCheck(PromptConfig config) {
        if (!StringUtils.hasText(config.getPromptKey())) {
            throw new RuntimeException("指令标识不能为空");
        }
        if (!StringUtils.hasText(config.getName())) {
            throw new RuntimeException("指令名称不能为空");
        }
        if (this.lambdaQuery().eq(PromptConfig::getPromptKey, config.getPromptKey()).count() > 0) {
            throw new RuntimeException("指令标识已存在：" + config.getPromptKey());
        }
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            this.baseMapper.clearDefaultByScene(config.getScene());
        }
        config.setStatus(config.getStatus() != null ? config.getStatus() : 1);
        return this.save(config);
    }

    @Override
    @Transactional
    public boolean updateWithCheck(PromptConfig config) {
        if (config.getId() == null) {
            throw new RuntimeException("ID不能为空");
        }
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            this.baseMapper.clearDefaultByScene(config.getScene());
        }
        return this.updateById(config);
    }

    @Override
    public boolean toggleStatus(Long id) {
        PromptConfig config = this.getById(id);
        if (config == null) {
            throw new RuntimeException("提示词配置不存在");
        }
        config.setStatus(config.getStatus() == 1 ? 0 : 1);
        return this.updateById(config);
    }

    @Override
    public boolean removeConfig(Long id) {
        return this.removeById(id);
    }
}