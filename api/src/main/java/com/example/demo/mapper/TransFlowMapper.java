package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.TransFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交易流水 Mapper
 * 继承 BaseMapper 后自动拥有 insert/selectById/selectPage 等通用方法
 * 流水只做插入和查询，不做更新，所以不需要自定义 XML
 */
@Mapper
public interface TransFlowMapper extends BaseMapper<TransFlow> {

    /**
     * 汇总某用户、指定交易类型、指定状态的流水金额合计。
     *
     * <p>用途：计算「在途提现占用额度」—— 提现在受理时只落一条 PROCESSING 流水，
     * 银行余额要等回调才减少，因此必须把在途金额从可用余额里扣掉。
     *
     * <p>注意：手写注解 SQL <b>必须显式写 {@code is_deleted = 0}</b>。
     * {@code @TableLogic} 的自动过滤条件只会被 MyBatis-Plus 生成的 SQL 追加，
     * 注解 SQL 不会 —— 漏掉会把已逻辑删除的流水也算进占用。
     *
     * @return 合计金额；无匹配行时由 IFNULL 兜底为 0，调用方无需判空
     */
    @Select("SELECT IFNULL(SUM(trans_amount), 0) FROM trans_flow "
            + "WHERE user_id = #{userId} AND trans_type = #{transType} "
            + "AND status = #{status} AND is_deleted = 0")
    BigDecimal sumAmount(@Param("userId") Long userId,
                         @Param("transType") Integer transType,
                         @Param("status") Integer status);

    /**
     * 汇总某用户、多交易类型、指定状态的流水金额合计。
     *
     * <p>用途：P0-1「统一可用额口径」。银行侧 <b>扣款一律不校验余额</b>（javap 确认
     * {@code lockAccount} / {@code withdraw} / {@code transferAccounts} 都是无条件加减），
     * 所以平台必须在发起前把所有「已受理、银行尚未回调」的扣款类在途金额从可用额里扣掉，
     * 否则并发两笔就能把账户扣成负数。
     *
     * <p>扣款类 = 提现(2) / 投标(3) / 还款(6)。充值(1) 是加钱、放款(5) 与回款(4) 不由用户余额出，
     * 都不应占用可用额。
     *
     * <p>同样必须显式写 {@code is_deleted = 0}，理由见 {@link #sumAmount}。
     *
     * @return 合计金额；无匹配行时由 IFNULL 兜底为 0，调用方无需判空
     */
    @Select("<script>"
            + "SELECT IFNULL(SUM(trans_amount), 0) FROM trans_flow "
            + "WHERE user_id = #{userId} AND status = #{status} AND is_deleted = 0 "
            + "AND trans_type IN "
            + "<foreach collection='transTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach>"
            + "</script>")
    BigDecimal sumAmountByTypes(@Param("userId") Long userId,
                                @Param("transTypes") List<Integer> transTypes,
                                @Param("status") Integer status);
}
