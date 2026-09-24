package com.kzip.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kzip.app.entity.ChatModelBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatModelBindingMapper extends BaseMapper<ChatModelBinding> {

    @Select("SELECT * FROM ai_chat_model_binding WHERE scene = #{scene} AND enabled = 1 AND is_deleted = 0 LIMIT 1")
    ChatModelBinding selectActiveByScene(@Param("scene") String scene);

    @Select("SELECT * FROM ai_chat_model_binding WHERE scene = #{scene} AND enabled = 1 AND is_deleted = 0")
    ChatModelBinding selectByScene(@Param("scene") String scene);

    @Select("SELECT * FROM ai_chat_model_binding WHERE enabled = 1 AND is_deleted = 0")
    List<ChatModelBinding> selectAllEnabled();
}