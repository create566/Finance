package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.Dict;

import java.util.List;

/**
 * 字典 Service
 * 负责：分类树查询、按编码查选项、后台 CRUD
 */
public interface DictService extends IService<Dict> {

    /**
     * 查询全部分类树（ROOT 下的所有分类节点）
     */
    List<Dict> listCategories();

    /**
     * 按分类编码查选项列表（如 returnMethod → 等额本息/等额本金...）
     */
    List<Dict> listByDictCode(String dictCode);

    /**
     * 新增分类节点
     */
    Dict saveCategory(String dictCode, String name);

    /**
     * 新增选项节点
     */
    Dict saveOption(Long parentId, String name, Integer value);

    /**
     * 更新字典节点（分类或选项）
     */
    boolean updateDict(Dict dict);

    /**
     * 删除字典节点（分类删除时连带删除其下选项）
     */
    boolean removeDictById(Long id);
}
