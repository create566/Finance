package com.example.demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.BorrowInfo;
import com.example.demo.entity.Lend;

public interface LendService extends IService<Lend> {

    /**
     * 根据借款申请生成标的（审批通过时调用）
     * 从 BorrowInfo 拷贝金额、利率、期限等字段，不重新计算利率
     */
    Lend createLend(BorrowInfo borrowInfo);

    /**
     * 标的分页列表
     */
    Page<Lend> pageList(Integer pageNum, Integer pageSize, String keyword, Integer status);

    /**
     * 发布标的（待发布→募资中）。
     * <p>发布即"上架审核通过"，会把操作管理员与时间写入 lend.check_admin_id / check_time 留痕。
     *
     * @param lendId  标的ID
     * @param adminId 操作管理员ID（后台登录态），仅用于留痕，不参与业务校验
     */
    boolean publish(Long lendId, Long adminId);

    /**
     * 下架标的：退出募集、退还投资人冻结资金、作废待签合同、通知双方。
     *
     * @param lendId  标的ID
     * @param adminId 操作管理员ID（后台登录态）。<b>处置投资人资金必须留痕，不可为 null</b>；
     *                传入 null 会被拒绝（抛 ResourceNotFoundException），而不是静默写成匿名操作。
     */
    boolean offline(Long lendId, Long adminId);

    /**
     * 前台投资人分页查询募资中的标的（按发布时间倒序）
     */
    Page<Lend> listFundingLends(Integer pageNum, Integer pageSize);

    /**
     * 标的流标（超时未募满）：
     * 1. 校验标的状态为募资中
     * 2. 批量将该标的所有"已投标"的投资记录标记为"已撤标"
     * 3. 银行侧自动解冻投资人冻结资金（与 cancelInvest 同一机制）
     * 4. 标的状态改为"已流标"
     * 5. 发送流标通知（给借款人和所有投资人）
     *
     * @param lendId 标的ID
     * @return 是否流标成功
     */
    boolean failLend(Long lendId);

}
