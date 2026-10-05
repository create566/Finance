package com.kzip.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kzip.app.entity.KnowledgeChunk;
import com.kzip.app.mapper.KnowledgeChunkMapper;
import com.kzip.app.service.KnowledgeChunkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class KnowledgeChunkServiceImpl extends ServiceImpl<KnowledgeChunkMapper, KnowledgeChunk> implements KnowledgeChunkService {

    @Override
    public List<KnowledgeChunk> listByDocId(Long docId) {
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeChunk::getDocId, docId)
                .orderByAsc(KnowledgeChunk::getChunkIndex);
        return this.list(wrapper);
    }

    @Override
    @Transactional
    public boolean batchSave(List<KnowledgeChunk> chunks) {
        return this.saveBatch(chunks);
    }

    @Override
    public boolean disableByDocId(Long docId) {
        return this.baseMapper.disableByDocId(docId) > 0;
    }

    @Override
    public boolean toggleStatus(Long id) {
        KnowledgeChunk chunk = this.getById(id);
        if (chunk == null) {
            throw new IllegalArgumentException("切片不存在");
        }
        chunk.setStatus(chunk.getStatus() == 1 ? 0 : 1);
        return this.updateById(chunk);
    }
}