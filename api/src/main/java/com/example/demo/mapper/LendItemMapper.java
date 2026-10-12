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
     *
     * <p>⚠️ `is_deleted = 0` 必须显式写：{@code Lend} 上的 {@code @TableLogic} 只对
     * MyBatis-Plus 自动生成的 SQL 生效，**对手写注解 SQL 不生效**。
     * 漏掉它的后果是：已逻辑删除、但 status 仍为 1 的标的会继续被累加募集额。</p>
     *
     * @return 影响行数 0=失败（标的不在募资中或已满/超募）
     */
    @Update("UPDATE lend SET invest_amount = invest_amount + #{amount}, invest_num = invest_num + 1 " +
            "WHERE id = #{lendId} AND is_deleted = 0 AND status = 1 AND amount - invest_amount >= #{amount}")
    int atomicInvest(@Param("lendId") Long lendId, @Param("amount") BigDecimal amount);

    /**
     * 原子回减标的已募集金额（撤标专用），与 {@link #atomicInvest} 严格对称。
     *
     * <p><b>为什么必须原子：</b>撤标改造前走的是"读-改-写"——
     * {@code lend.setInvestAmount(lend.getInvestAmount() - amount); updateById(lend);}。
     * 两个投资人同时撤标时，两者读到同一份 {@code invest_amount}，
     * 后写的那个把先写的覆盖掉 ⇒ 标的已投金额<b>永久偏高</b>，
     * 表现为"剩余可投金额变少、但实际已投没那么多"，一路传递到放款时的
     * "Σ投资份额 ≠ 标的金额"。改为 {@code invest_amount = invest_amount - ?}
     * 由数据库串行化，配合 {@code invest_amount >= #{amount}} 兜住不下溢。</p>
     *
     * <p>⚠️ {@code is_deleted = 0} 同样必须显式写：{@code @TableLogic} 对手写注解 SQL 不生效。</p>
     *
     * @return 影响行数 0=失败（标的不在募资中 / 已投金额不足 / 标的不存在或已删）
     */
    @Update("UPDATE lend SET invest_amount = invest_amount - #{amount}, invest_num = invest_num - 1 " +
            "WHERE id = #{lendId} AND is_deleted = 0 AND status = 1 AND invest_amount >= #{amount}")
    int atomicCancelInvest(@Param("lendId") Long lendId, @Param("amount") BigDecimal amount);

    /**
     * 把投资记录原子翻转为「已撤标(1)」——<b>退资的幂等闸门</b>。
     *
     * <p><b>为什么不直接 updateById：</b>退资改造前是「读内存 status → 判断 → updateById」，
     * 这道闸门在下面两种场景会同时放行两笔退款：</p>
     * <ol>
     *   <li>用户连点两次"撤标"（前端无幂等键）；</li>
     *   <li>用户撤标 与 管理端下架/流标触发的批量退资 并发执行。</li>
     * </ol>
     * <p>两笔都通过校验 ⇒ 同一笔投资<b>退款两次</b>：镜像余额多加一次钱、多一条退款流水，
     * 而银行只解冻一次 —— 平台凭空多付给投资人一笔，直接破坏资金闭环。</p>
     *
     * <p>改为数据库层 CAS（{@code status IN (0,2)} → 1）后，只有一行能更新成功，
     * 拿到 0 行的那个调用方直接短路，不产生任何副作用。</p>
     *
     * @return 影响行数 0=已被撤标或状态不可退资（调用方须跳过退资）
     */
    @Update("UPDATE lend_item SET status = 1, update_time = NOW() " +
            "WHERE id = #{itemId} AND is_deleted = 0 AND status IN (0, 2)")
    int atomicMarkCancelled(@Param("itemId") Long itemId);

    /**
     * 覆盖某笔投资的「预期收益」为确定值（放款生成回款计划后统一口径用，见恒等式⑧）。
     *
     * <p>用一条定向 SQL 而不是 MyBatis-Plus 的 {@code LambdaUpdateWrapper}：后者在
     * 构造 {@code set(LendItem::getExpectAmount, ...)} 时会去查实体的 lambda 缓存，
     * 在没有真正初始化 MyBatis 的环境（如纯 Mockito 单测）里会抛
     * {@code can not find lambda cache for this entity} —— 把一个纯数据写入动作
     * 绑死在框架启动状态上，得不偿失。</p>
     *
     * @return 影响行数 0 = 记录不存在或已逻辑删除
     */
    @Update("UPDATE lend_item SET expect_amount = #{expectAmount}, update_time = NOW() " +
            "WHERE id = #{itemId} AND is_deleted = 0")
    int updateExpectAmount(@Param("itemId") Long itemId, @Param("expectAmount") BigDecimal expectAmount);
}
