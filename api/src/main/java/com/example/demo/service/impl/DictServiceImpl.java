package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.Dict;
import com.example.demo.mapper.DictMapper;
import com.example.demo.service.DictService;
import com.wwfinance.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class DictServiceImpl extends ServiceImpl<DictMapper, Dict> implements DictService {

    /** ROOT 节点 ID */
    private static final Long ROOT_ID = 1L;

    @Override
    public List<Dict> listCategories() {
        return this.list(new LambdaQueryWrapper<Dict>()
                .eq(Dict::getParentId, ROOT_ID)
                .isNotNull(Dict::getDictCode)
                .orderByAsc(Dict::getId));
    }

    @Override
    public List<Dict> listByDictCode(String dictCode) {
        // 先查分类节点，拿到其 id
        Dict category = this.getOne(new LambdaQueryWrapper<Dict>()
                .eq(Dict::getDictCode, dictCode)
                .last("LIMIT 1"));
        if (category == null) {
            throw new ResourceNotFoundException(500, "字典分类不存在：" + dictCode);
        }
        // 查该分类下的选项
        return this.list(new LambdaQueryWrapper<Dict>()
                .eq(Dict::getParentId, category.getId())
                .orderByAsc(Dict::getValue));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Dict saveCategory(String dictCode, String name) {
        // 校验编码不重复
        long count = this.count(new LambdaQueryWrapper<Dict>()
                .eq(Dict::getDictCode, dictCode));
        if (count > 0) {
            throw new ResourceNotFoundException(500, "字典编码已存在：" + dictCode);
        }
        Dict dict = new Dict()
                .setParentId(ROOT_ID)
                .setDictCode(dictCode)
                .setName(name);
        this.save(dict);
        log.info("新增字典分类：dictCode={}, name={}", dictCode, name);
        return dict;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Dict saveOption(Long parentId, String name, Integer value) {
        Dict parent = this.getById(parentId);
        if (parent == null) {
            throw new ResourceNotFoundException(500, "父分类不存在");
        }
        if (parent.getDictCode() == null || parent.getDictCode().isEmpty()) {
            throw new ResourceNotFoundException(500, "父节点不是分类节点");
        }
        Dict dict = new Dict()
                .setParentId(parentId)
                .setName(name)
                .setValue(value);
        this.save(dict);
        log.info("新增字典选项：parentId={}, name={}, value={}", parentId, name, value);
        return dict;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDict(Dict dict) {
        if (dict.getId() == null) {
            throw new ResourceNotFoundException(500, "id不能为空");
        }
        // 分类节点修改编码时校验不重复
        if (dict.getDictCode() != null && !dict.getDictCode().isEmpty()) {
            long count = this.count(new LambdaQueryWrapper<Dict>()
                    .eq(Dict::getDictCode, dict.getDictCode())
                    .ne(Dict::getId, dict.getId()));
            if (count > 0) {
                throw new ResourceNotFoundException(500, "字典编码已存在：" + dict.getDictCode());
            }
        }
        return this.updateById(dict);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeDictById(Long id) {
        Dict dict = this.getById(id);
        if (dict == null) {
            throw new ResourceNotFoundException(500, "字典节点不存在");
        }
        // 分类节点：连带删除其下所有选项
        long childCount = this.count(new LambdaQueryWrapper<Dict>()
                .eq(Dict::getParentId, id));
        if (childCount > 0) {
            this.remove(new LambdaQueryWrapper<Dict>()
                    .eq(Dict::getParentId, id));
        }
        boolean ok = this.removeById(id);
        log.info("删除字典节点：id={}, 子节点数={}", id, childCount);
        return ok;
    }
}
