package com.example.demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.ContractTemplate;

/**
 * 合同模板服务
 */
public interface ContractTemplateService extends IService<ContractTemplate> {

    Page<ContractTemplate> pageList(String contractType, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 新增模板（version 自动取该 templateNo 的下一版）
     */
    ContractTemplate saveTemplate(ContractTemplate tpl);

    /**
     * 修改模板：**不覆盖原记录**，而是生成 version+1 的新版本。
     * <p>
     * 已签署的合同正文快照已冻结，模板改版只影响之后新生成的合同；保留旧版本行便于追溯。
     */
    ContractTemplate updateTemplate(Long id, ContractTemplate tpl);

    void toggleStatus(Long id, Integer status);
}
