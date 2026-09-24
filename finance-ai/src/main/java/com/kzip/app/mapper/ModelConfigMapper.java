package com.kzip.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kzip.app.entity.ModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {

    @Select("SELECT * FROM ai_model_config WHERE model_type = #{modelType} AND status = 1 AND is_deleted = 0")
    List<ModelConfig> selectActiveByType(@Param("modelType") String modelType);

    @Update("UPDATE ai_model_config SET is_default = 0 WHERE model_type = #{modelType} AND is_deleted = 0")
    int clearDefaultByType(@Param("modelType") String modelType);
}