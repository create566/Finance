package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.TransFlow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易流水 Mapper
 * 继承 BaseMapper 后自动拥有 insert/selectById/selectPage 等通用方法
 * 流水只做插入和查询，不做更新，所以不需要自定义 XML
 */
@Mapper
public interface TransFlowMapper extends BaseMapper<TransFlow> {
}
