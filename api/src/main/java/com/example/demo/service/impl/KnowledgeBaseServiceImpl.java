package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.KnowledgeBase;
import com.example.demo.entity.KnowledgeDoc;
import com.example.demo.exception.BusinessException;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.example.demo.mapper.KnowledgeBaseMapper;
import com.example.demo.service.KnowledgeBaseService;
import com.example.demo.service.KnowledgeDocService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.utils.PageUtils;

@Slf4j
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    @Autowired
    private KnowledgeDocService knowledgeDocService;

    @Override
    public IPage<KnowledgeBase> pageList(Integer pageNum, Integer pageSize, String name, Integer status) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(KnowledgeBase::getName, name.trim());
        }
        if (status != null) {
            wrapper.eq(KnowledgeBase::getStatus, status);
        }
        wrapper.orderByDesc(KnowledgeBase::getUpdateTime);
        return this.page(PageUtils.of(pageNum, pageSize), wrapper);
    }

    @Override
    public boolean saveWithCheck(KnowledgeBase knowledgeBase) {
        if (knowledgeBase.getName() == null || knowledgeBase.getName().trim().isEmpty()) {
            throw new RuntimeException("知识库名称不能为空");
        }
        if (knowledgeBase.getEmbeddingModel() == null || knowledgeBase.getEmbeddingModel().trim().isEmpty()) {
            knowledgeBase.setEmbeddingModel("text-embedding-ada-002");
        }
        knowledgeBase.setDocCount(0);
        knowledgeBase.setChunkCount(0);
        knowledgeBase.setStatus(knowledgeBase.getStatus() != null ? knowledgeBase.getStatus() : 1);
        return this.save(knowledgeBase);
    }

    @Override
    public boolean updateWithCheck(KnowledgeBase knowledgeBase) {
        if (knowledgeBase.getId() == null) {
            throw new RuntimeException("知识库ID不能为空");
        }
        KnowledgeBase existing = this.getById(knowledgeBase.getId());
        if (existing == null) {
            throw new RuntimeException("知识库不存在");
        }
        return this.updateById(knowledgeBase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeCascade(Long id) {
        KnowledgeBase existing = this.getById(id);
        if (existing == null) {
            throw new ResourceNotFoundException(404, "知识库不存在");
        }
        knowledgeDocService.remove(new LambdaQueryWrapper<KnowledgeDoc>().eq(KnowledgeDoc::getKbId, id));
        boolean ok = this.removeById(id);
        log.info("级联删除知识库: id={}, 名称={}", id, existing.getName());
        return ok;
    }

    @Override
    public void updateStatistics(Long kbId) {
        long docCount = knowledgeDocService.count(
                new LambdaQueryWrapper<KnowledgeDoc>().eq(KnowledgeDoc::getKbId, kbId));
        long chunkCount = knowledgeDocService.list(
                        new LambdaQueryWrapper<KnowledgeDoc>().eq(KnowledgeDoc::getKbId, kbId))
                .stream()
                .mapToLong(doc -> doc.getChunkCount() != null ? doc.getChunkCount() : 0)
                .sum();
        KnowledgeBase kb = this.getById(kbId);
        if (kb != null) {
            kb.setDocCount((int) docCount);
            kb.setChunkCount((int) chunkCount);
            this.updateById(kb);
            log.debug("更新知识库统计: kbId={}, docCount={}, chunkCount={}", kbId, docCount, chunkCount);
        }
    }
}