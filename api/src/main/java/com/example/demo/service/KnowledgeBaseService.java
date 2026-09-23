package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.KnowledgeBase;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    IPage<KnowledgeBase> pageList(Integer pageNum, Integer pageSize, String name, Integer status);

    boolean saveWithCheck(KnowledgeBase knowledgeBase);

    boolean updateWithCheck(KnowledgeBase knowledgeBase);

    boolean removeCascade(Long id);

    void updateStatistics(Long kbId);
}