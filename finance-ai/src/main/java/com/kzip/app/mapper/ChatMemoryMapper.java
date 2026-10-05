package com.kzip.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kzip.app.entity.ChatMemoryMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMemoryMapper extends BaseMapper<ChatMemoryMessage> {

    @Select("SELECT * FROM chat_memory WHERE conversation_id = #{conversationId} ORDER BY seq ASC")
    List<ChatMemoryMessage> selectByConversationId(@Param("conversationId") String conversationId);

    @Delete("DELETE FROM chat_memory WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(@Param("conversationId") String conversationId);

    @Select("SELECT DISTINCT conversation_id FROM chat_memory")
    List<String> selectDistinctConversationIds();
}
