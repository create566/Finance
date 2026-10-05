package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.ContractTemplate;
import com.example.demo.enums.ContractTypeEnum;
import com.example.demo.mapper.ContractTemplateMapper;
import com.example.demo.service.ContractTemplateService;
import com.wwfinance.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.example.demo.utils.PageUtils;

@Slf4j
@Service
public class ContractTemplateServiceImpl extends ServiceImpl<ContractTemplateMapper, ContractTemplate>
        implements ContractTemplateService {

    @Override
    public Page<ContractTemplate> pageList(String contractType, Integer status, Integer pageNum, Integer pageSize) {
        Page<ContractTemplate> page = PageUtils.of(pageNum, pageSize);
        LambdaQueryWrapper<ContractTemplate> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(contractType), ContractTemplate::getContractType, contractType);
        w.eq(status != null, ContractTemplate::getStatus, status);
        w.orderByDesc(ContractTemplate::getUpdateTime).orderByDesc(ContractTemplate::getId);
        return this.page(page, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractTemplate saveTemplate(ContractTemplate tpl) {
        validate(tpl);
        if (!StringUtils.hasText(tpl.getTemplateNo())) {
            tpl.setTemplateNo("TPL_" + tpl.getContractType());
        }
        int next = nextVersion(tpl.getTemplateNo());
        tpl.setId(null);
        tpl.setVersion(next);
        if (tpl.getStatus() == null) {
            tpl.setStatus(1);
        }
        this.save(tpl);
        // 同一 templateNo 只保留一个启用版本：
        // 运行时按 `status=1 ORDER BY version DESC LIMIT 1` 取模板，若旧版本仍启用，
        // 管理员"停用最新版本"会静默回退到旧版本（看着像停用没生效），且后台列表会出现
        // 多条"启用中"导致无法判断哪条真正生效。这里在新版本落库后退役同编号的旧版本。
        retireOtherVersions(tpl.getTemplateNo(), tpl.getId());
        log.info("合同模板新增: no={}, version={}, type={}", tpl.getTemplateNo(), next, tpl.getContractType());
        return tpl;
    }

    /** 把同 templateNo 下除 keepId 之外的启用版本置为停用 */
    private void retireOtherVersions(String templateNo, Long keepId) {
        boolean retired = this.update(new LambdaUpdateWrapper<ContractTemplate>()
                .eq(ContractTemplate::getTemplateNo, templateNo)
                .eq(ContractTemplate::getStatus, 1)
                .ne(ContractTemplate::getId, keepId)
                .set(ContractTemplate::getStatus, 0));
        if (retired && log.isDebugEnabled()) {
            log.debug("合同模板历史版本已退役: no={}, 保留 id={}", templateNo, keepId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractTemplate updateTemplate(Long id, ContractTemplate tpl) {
        ContractTemplate old = this.getById(id);
        if (old == null) {
            throw new ResourceNotFoundException(500, "模板不存在");
        }
        // 不覆盖历史行：以旧行的 templateNo 生成一条新版本，正文留痕可追溯
        ContractTemplate fresh = new ContractTemplate();
        fresh.setTemplateNo(old.getTemplateNo());
        fresh.setContractType(StringUtils.hasText(tpl.getContractType())
                ? tpl.getContractType() : old.getContractType());
        fresh.setTitle(StringUtils.hasText(tpl.getTitle()) ? tpl.getTitle() : old.getTitle());
        fresh.setContentTpl(StringUtils.hasText(tpl.getContentTpl()) ? tpl.getContentTpl() : old.getContentTpl());
        fresh.setVariables(tpl.getVariables() != null ? tpl.getVariables() : old.getVariables());
        fresh.setStatus(old.getStatus() == null ? 1 : old.getStatus());
        return saveTemplate(fresh);
    }

    @Override
    public void toggleStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new ResourceNotFoundException(400, "状态值只能是 0（停用）或 1（启用）");
        }
        ContractTemplate t = this.getById(id);
        if (t == null) {
            throw new ResourceNotFoundException(500, "模板不存在");
        }
        t.setStatus(status);
        this.updateById(t);
        log.info("合同模板状态变更: id={}, status={}", id, status);
    }

    private int nextVersion(String templateNo) {
        ContractTemplate last = this.getOne(new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getTemplateNo, templateNo)
                .orderByDesc(ContractTemplate::getVersion)
                .last("LIMIT 1"));
        return last == null || last.getVersion() == null ? 1 : last.getVersion() + 1;
    }

    private void validate(ContractTemplate tpl) {
        if (!StringUtils.hasText(tpl.getContractType())) {
            throw new ResourceNotFoundException(400, "合同类型不能为空");
        }
        if (ContractTypeEnum.fromCode(tpl.getContractType()) == null) {
            throw new ResourceNotFoundException(400,
                    "合同类型只能是 LOAN / INVEST / PAYMENT，当前值：" + tpl.getContractType());
        }
        if (!StringUtils.hasText(tpl.getTitle())) {
            throw new ResourceNotFoundException(400, "合同标题不能为空");
        }
        if (!StringUtils.hasText(tpl.getContentTpl())) {
            throw new ResourceNotFoundException(400, "合同正文不能为空");
        }
    }
}
