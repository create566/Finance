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
     * 由定时任务执行的系统自动退资处置：借款人满标后超期未签《放款确认书》时调用。
     *
     * <p>与 {@link #offline(Long, Long)} 的差异：本方法没有人类操作人，
     * 落库时 {@code offline_admin_id} 为 NULL（审计上表示"系统自动处置"），
     * 通知文案也改为"因借款人超期未签"而非"平台下架"。</p>
     *
     * @return 处置成功返回 true；标的不存在或状态不合规返回 false / 抛业务异常
     */
    boolean offlineBySystem(Long lendId);

    /**
     * 借款人签署《放款确认书》的宽限期（自然日）。
     *
     * <p><b>必须与合同模板 TPL_LOAN / TPL_INVEST 中的条款天数保持一致</b> ——
     * 平台是依据合同里那句事先约定才有权处置投资人的冻结资金的，
     * 代码写 7 天、合同写 3 天，处置行为就失去了依据。</p>
     */
    int MAKE_LOAN_SIGN_DEADLINE_DAYS = 3;

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
