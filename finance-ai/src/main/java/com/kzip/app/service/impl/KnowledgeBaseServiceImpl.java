package com.kzip.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kzip.app.entity.KnowledgeBase;
import com.kzip.app.mapper.ChatModelBindingMapper;
import com.kzip.app.mapper.KnowledgeBaseMapper;
import com.kzip.app.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private ChatModelBindingMapper chatModelBindingMapper;

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
    @Transactional
    public boolean removeBase(Long id) {
        // 解除「对话模型选择」对该知识库的引用，避免留下悬空 kb_id
        // （悬空后再次保存该绑定会直接报"所选知识库不存在"）
        int cleared = chatModelBindingMapper.clearKbByKbId(id);
        if (cleared > 0) {
            log.info("知识库 {} 删除，已解除 {} 条场景绑定的知识库引用", id, cleared);
        }
        return this.removeById(id);
    }

    @Override
    public void incrementDocCount(Long kbId, int deltaChunkCount) {
        // 原子 SQL 自增，避免并发读取-计算-写入丢失更新
        knowledgeBaseMapper.incrementDocAndChunk(kbId, deltaChunkCount);
    }

    @Override
    public void decrementDocCount(Long kbId, int deltaChunkCount) {
        // 原子 SQL 自减（下限 0）
        knowledgeBaseMapper.decrementDocAndChunk(kbId, deltaChunkCount);
    }

    @Override
    public void addChunkCount(Long kbId, int chunkCount) {
        // 原子 SQL 自增（仅切片数）
        knowledgeBaseMapper.incrementChunkOnly(kbId, chunkCount);
    }
}