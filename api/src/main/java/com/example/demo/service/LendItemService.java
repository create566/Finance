package com.example.demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.LendItem;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface LendItemService extends IService<LendItem> {

    String buildInvestForm(Long lendId, Long userId, BigDecimal amount);

    boolean cancelInvest(Long lendItemId, Long userId);

    /**
     * 【P4-19】退资处置：把一条投资记录置为"已撤标(1)"，并回补平台镜像余额 + 记录退款流水。
     *
     * <p>投标回调曾执行 {@code updateBalance(user, INVEST, amount)} 从 {@code user_account}
     * 扣减镜像，退资时必须对称加回，否则镜像余额单向减少、永久偏低。
     * 供 {@code LendServiceImpl#withdrawAllInvestments}（下架/流标/超期未签）复用，
     * 与用户主动撤标 {@link #cancelInvest} 保持同一套机制。</p>
     *
     * @param lendItem 待退还的投资记录
     * @param memo     退款原因（写入流水备注，便于与银行逐笔对账）
     * @return 是否处理成功
     */
    boolean refundInvestment(LendItem lendItem, String memo);

    boolean handleInvestNotify(Map<String, Object> params);

    List<LendItem> listByLendId(Long lendId);

    /**
     * 【恒等式⑧】把某笔投资的「预期收益」改写为指定值。
     *
     * <p>投资发生的那一刻还没有还款计划，{@code expect_amount} 只能按
     * (金额, 利率, 期数, 还款方式) 预估；放款生成回款计划之后，该户实际能收到的利息
     * 已成为确定值（Σ {@code lend_item_return.interest}），此时用它覆盖预估值，
     * 保证「页面显示预期收益 ≡ 投资人实际到账利息」。</p>
     *
     * @param lendItemId   投资记录ID
     * @param expectAmount 利息合计（2 位小数）
     * @return 是否更新成功
     */
    boolean updateExpectAmount(Long lendItemId, BigDecimal expectAmount);

    Page<LendItem> pageList(Integer pageNum, Integer pageSize, Long lendId, Long investUserId);

    User getUserForInvest(Long userId);

    UserBind getBindForInvest(Long userId);

    BigDecimal getBalanceForInvest(Long userId);
}