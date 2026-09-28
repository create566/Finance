package com.kzip.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kzip.app.entity.KnowledgeBase;
import com.kzip.app.mapper.KnowledgeBaseMapper;
import com.kzip.app.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    @Override
    public List<KnowledgeBase> listAll(String keyword, Integer status) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(KnowledgeBase::getName, keyword);
        }
        wrapper.eq(status != null, KnowledgeBase::getStatus, status);
        wrapper.orderByDesc(KnowledgeBase::getUpdateTime);
        return this.list(wrapper);
    }

    @Override
    public boolean saveBase(KnowledgeBase base) {
        if (base.getDocCount() == null) base.setDocCount(0);
        if (base.getChunkCount() == null) base.setChunkCount(0);
        if (base.getStatus() == null) base.setStatus(1);
        return this.save(base);
    }

    @Override
    public boolean updateBase(KnowledgeBase base) {
        base.setUpdateTime(LocalDateTime.now());
        return this.updateById(base);
    }

    @Override
    public boolean removeBase(Long id) {
        return this.removeById(id);
    }

    @Override
    public void incrementDocCount(Long kbId, int deltaChunkCount) {
        KnowledgeBase kb = this.getById(kbId);
        if (kb != null) {
            kb.setDocCount((kb.getDocCount() == null ? 0 : kb.getDocCount()) + 1);
            kb.setChunkCount((kb.getChunkCount() == null ? 0 : kb.getChunkCount()) + deltaChunkCount);
            kb.setUpdateTime(LocalDateTime.now());
            this.updateById(kb);
        }
    }

    @Override
    public void decrementDocCount(Long kbId, int deltaChunkCount) {
        KnowledgeBase kb = this.getById(kbId);
        if (kb != null) {
            kb.setDocCount(Math.max(0, (kb.getDocCount() == null ? 0 : kb.getDocCount()) - 1));
            kb.setChunkCount(Math.max(0, (kb.getChunkCount() == null ? 0 : kb.getChunkCount()) - deltaChunkCount));
            kb.setUpdateTime(LocalDateTime.now());
            this.updateById(kb);
        }
    }

    @Override
    public void addChunkCount(Long kbId, int chunkCount) {
        KnowledgeBase kb = this.getById(kbId);
        if (kb != null) {
            kb.setChunkCount((kb.getChunkCount() == null ? 0 : kb.getChunkCount()) + chunkCount);
            kb.setUpdateTime(LocalDateTime.now());
            this.updateById(kb);
        }
    }
}