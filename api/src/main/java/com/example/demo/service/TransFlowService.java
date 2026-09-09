package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.TransFlow;

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
     * @param userId 用户id
     * @param page   页码
     * @param limit  每页条数
     * @return 分页结果
     */
    IPage<TransFlow> listByUser(Long userId, long page, long limit);

}
