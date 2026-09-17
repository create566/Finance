package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.LendItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 投资记录 Mapper
 */
@Mapper
public interface LendItemMapper extends BaseMapper<LendItem> {

    /**
     * 原子累加标的已募集金额并防超募
     * 条件：lend.status=1（募资中）且剩余可投>=本次投资额
     * @return 影响行数 0=失败（标的不在募资中或已满/超募）
     */
    @Update("UPDATE lend SET invest_amount = invest_amount + #{amount}, invest_num = invest_num + 1 " +
            "WHERE id = #{lendId} AND status = 1 AND amount - invest_amount >= #{amount}")
    int atomicInvest(@Param("lendId") Long lendId, @Param("amount") BigDecimal amount);
}
