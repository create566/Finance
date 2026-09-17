package com.example.demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.LendItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface LendItemService extends IService<LendItem> {

    /**
     * 发起投标（跳转旺旺银行）：
     * 校验投资人/标的/金额/余额/绑卡 → 组装银行参数+签名 → 返回自动提交表单HTML
     * 余额扣减不在本方法执行，由银行异步回调驱动（合规：资金不过平台账户）
     *
     * @param lendId 标的ID
     * @param userId 投资人ID
     * @param amount 投资金额
     * @return 跳转到银行页面的 HTML 表单串
     */
    String buildInvestForm(Long lendId, Long userId, BigDecimal amount);

    /**
     * 撤消投资（满标前）：解冻金额 + 更新状态为已撤标
     *
     * @param lendItemId 投资记录ID
     * @param userId     投资人ID
     * @return 是否撤标成功
     */
    boolean cancelInvest(Long lendItemId, Long userId);

    /**
     * 处理银行投标异步回调：验签 → 状态/金额校验 → 幂等 → 原子更新标的 → 扣减余额 → 记录投标 → 满标检查
     *
     * @param params 银行回调的全部参数（含 sign）
     * @return true=业务已受理（应答 success）；false=验签失败或数据异常（应答 fail）
     */
    boolean handleInvestNotify(Map<String, Object> params);

    /**
     * 查询标的的投资记录列表
     */
    List<LendItem> listByLendId(Long lendId);

    /**
     * 投资记录分页（管理端）
     */
    Page<LendItem> pageList(Integer pageNum, Integer pageSize, Long lendId, Long investUserId);

}
