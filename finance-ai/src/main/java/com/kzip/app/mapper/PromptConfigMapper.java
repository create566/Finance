package com.kzip.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kzip.app.entity.PromptConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PromptConfigMapper extends BaseMapper<PromptConfig> {

    @Select("SELECT * FROM prompt_config WHERE scene = #{scene} AND status = 1 AND is_deleted = 0 ORDER BY is_default DESC, id ASC")
    List<PromptConfig> selectActiveByScene(@Param("scene") String scene);

    @Update("UPDATE prompt_config SET is_default = 0 WHERE scene = #{scene} AND is_deleted = 0")
    int clearDefaultByScene(@Param("scene") String scene);
}