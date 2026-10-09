package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * ⚠️ `is_deleted = 0` 必须显式写：{@code ChatSession} 上的 {@code @TableLogic}
     * 只对 MyBatis-Plus 自动生成的 SQL 生效，**对手写注解 SQL 不生效**。
     */
    @Update("UPDATE chat_session SET message_count = message_count + 1, update_time = NOW() " +
            "WHERE id = #{id} AND is_deleted = 0")
    int incrementMessageCount(@Param("id") Long id);

    @Update("UPDATE chat_session SET status = 1, update_time = NOW() WHERE id = #{id} AND is_deleted = 0")
    int closeSession(@Param("id") Long id);
}