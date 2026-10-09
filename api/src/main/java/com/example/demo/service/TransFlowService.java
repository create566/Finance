package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.TransFlow;
import com.example.demo.enums.TransTypeEnum;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 交易流水 Service 接口
 * 继承 IService<TransFlow> 后自动拥有 save/getById/removeById/updateById 等通用方法
 * 这里只声明业务自定义方法
 */
public interface TransFlowService extends IService<TransFlow> {

    /**
     * 充值：加余额 + 记流水（同一事务，由银行回调驱动）
     *
     * @param userId      用户id
     * @param amount      充值金额
     * @param bankTransNo 银行流水号（用于幂等）
     */
    void recharge(Long userId, BigDecimal amount, String bankTransNo);

    /**
     * 提现：扣余额 + 记流水（同一事务，由银行回调驱动）
     * 余额不足会抛异常，事务回滚
     *
     * @param userId      用户id
     * @param amount      提现金额
     * @param bankTransNo 银行流水号（用于幂等）
     */
    void withdraw(Long userId, BigDecimal amount, String bankTransNo);

    /**
     * 判断某银行流水号是否已入账（回调幂等用）
     */
    boolean existsByTransNo(String bankTransNo);

    /**
     * 判断某单号是否已有「终态」流水（成功/失败）。
     *
     * 与 {@link #existsByTransNo} 的区别：充值/提现在用户发起时就已落了一条
     * 「处理中」流水，如果幂等判断把处理中的也算作"已入账"，
     * 银行回调会被误判成重复通知而直接吞掉 —— 那笔钱永远停在处理中。
     */
    boolean existsSettledByTransNo(String transNo);

    /**
     * 【P0-1】落一条「处理中」的在途流水，占用该用户的可用余额。
     *
     * <p>银行侧扣款一律不校验余额（javap 确认 {@code lockAccount}/{@code withdraw}/
     * {@code transferAccounts} 都是无条件加减），所以平台必须在<b>发起前</b>就把这笔钱占住，
     * 否则并发两笔投标/还款能把账户扣成负数 —— 库里现存的 3 个负余额账户就是这么来的。
     *
     * <p>原先只有充值/提现落 PROCESSING 流水，投标与还款不落 —— 三套口径各算各的。
     * 现在投标与还款也走这里，可用额口径才真正统一。
     *
     * @param transNo 业务单号（银行会原样回带，用于对账与结算），不可为空
     */
    void savePending(Long userId, TransTypeEnum type, BigDecimal amount, String memo, String transNo);

    /**
     * 【P0-1】把某条在途流水置为终态，释放或固化它对可用余额的占用。
     *
     * @param transNo 业务单号
     * @param success true=银行已成功扣款（置"成功"）；false=银行失败/用户撤销（置"失败"，释放占用）
     */
    void settlePending(String transNo, boolean success);

    /**
     * 处理银行充值异步回调：验签 → 校验状态/金额/用户 → 幂等判断 → 入账
     *
     * @param params 银行回调的全部参数（含 sign）
     * @return true=业务已受理（处理成功或银行状态非成功/重复回调，应答 success）；
     *         false=验签失败或数据异常（应答 fail，银行会重试）
     */
    boolean handleRechargeNotify(Map<String, Object> params);

    /**
     * 处理银行提现异步回调：验签 → 校验状态/金额/用户 → 幂等判断 → 扣账
     *
     * @param params 银行回调的全部参数（含 sign）
     * @return true=业务已受理；false=验签失败或数据异常（应答 fail）
     */
    boolean handleWithdrawNotify(Map<String, Object> params);

    /**
     * 查询当前用户的交易流水（分页，按时间倒序）
     *
     * @param userId    用户id
     * @param page      页码
     * @param limit     每页条数
     * @param transType 交易类型（可选，1充值2提现3投标4回款5放款6还款）
     * @return 分页结果
     */
    IPage<TransFlow> listByUser(Long userId, long page, long limit, Integer transType);

    /**
     * 管理端：全平台流水分页查询（按用户/类型/日期筛选）
     *
     * @param pageNum   页码
     * @param pageSize  每页条数
     * @param userId    用户ID（可选）
     * @param transType 交易类型（可选，1充值2提现3投标4回款5放款6还款）
     * @param userName  用户名/手机号模糊（可选）
     * @return 分页结果
     */
    IPage<TransFlow> adminPageList(Integer pageNum, Integer pageSize,
                                   Long userId, Integer transType, String userName);

}
