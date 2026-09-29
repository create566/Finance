package com.kzip.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kzip.app.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Update("UPDATE chat_session SET message_count = message_count + 1, update_time = NOW() WHERE id = #{id}")
    int incrementMessageCount(@Param("id") Long id);

    @Update("UPDATE chat_session SET status = 1, update_time = NOW() WHERE id = #{id}")
    int closeSession(@Param("id") Long id);

    @Update("UPDATE chat_session SET title = #{title}, update_time = NOW() WHERE id = #{id} AND (title IS NULL OR title = '')")
    int updateTitleIfEmpty(@Param("id") Long id, @Param("title") String title);
}