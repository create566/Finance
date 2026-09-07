package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.TransFlow;

import java.math.BigDecimal;

/**
 * 交易流水 Service 接口
 * 继承 IService<TransFlow> 后自动拥有 save/getById/removeById/updateById 等通用方法
 * 这里只声明业务自定义方法
 */
public interface TransFlowService extends IService<TransFlow> {

    /**
     * 充值：加余额 + 记流水（同一事务）
     *
     * @param userId 用户id
     * @param amount 充值金额
     */
    void recharge(Long userId, BigDecimal amount);

    /**
     * 提现：扣余额 + 记流水（同一事务）
     * 余额不足会抛异常，事务回滚
     *
     * @param userId 用户id
     * @param amount 提现金额
     */
    void withdraw(Long userId, BigDecimal amount);

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
