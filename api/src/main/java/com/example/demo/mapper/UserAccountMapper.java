package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {

    /**
     * 原子加钱：充值 / 回款时调用
     * 直接在 SQL 层做 amount = amount + ?，避免"先查后改"的并发问题
     *
     * @param userId 用户id
     * @param amount 加多少钱
     * @return 影响行数（0 表示账户不存在）
     */
    @Update("UPDATE user_account SET amount = amount + #{amount} " +
            "WHERE user_id = #{userId} AND is_deleted = 0")
    int addAmount(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子扣钱：提现 / 投标时调用
     * WHERE amount >= ? 保证余额不足时直接返回 0，不会扣成负数
     *
     * @param userId 用户id
     * @param amount 扣多少钱
     * @return 影响行数（0 表示余额不足或账户不存在）
     */
    @Update("UPDATE user_account SET amount = amount - #{amount} " +
            "WHERE user_id = #{userId} AND amount >= #{amount} AND is_deleted = 0")
    int deductAmount(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子冻结：投标时将可用余额转入冻结金额
     * WHERE amount >= ? 保证余额不足时直接返回 0
     */
    @Update("UPDATE user_account SET amount = amount - #{amount}, freeze_amount = freeze_amount + #{amount} " +
            "WHERE user_id = #{userId} AND amount >= #{amount} AND is_deleted = 0")
    int freezeAmount(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子解冻：流标/失败时将冻结金额退回可用余额
     */
    @Update("UPDATE user_account SET amount = amount + #{amount}, freeze_amount = freeze_amount - #{amount} " +
            "WHERE user_id = #{userId} AND freeze_amount >= #{amount} AND is_deleted = 0")
    int unfreezeAmount(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子扣减冻结：满标放款时将投资人冻结金额划转给借款人
     */
    @Update("UPDATE user_account SET freeze_amount = freeze_amount - #{amount} " +
            "WHERE user_id = #{userId} AND freeze_amount >= #{amount} AND is_deleted = 0")
    int deductFrozen(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

}
