package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper  extends BaseMapper<User> {

    /**
     * 原子扣减积分：GREATEST 兜底防负数
     * @return 影响行数（0 表示用户不存在）
     */
    @Update("UPDATE user SET integral = GREATEST(integral - #{points}, 0) " +
            "WHERE id = #{userId} AND is_deleted = 0")
    int deductIntegral(@Param("userId") Long userId, @Param("points") int points);

}
