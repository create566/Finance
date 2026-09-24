package com.kzip.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kzip.app.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} AND is_deleted = 0 ORDER BY create_time ASC")
    List<ChatMessage> selectBySessionId(@Param("sessionId") Long sessionId);
}