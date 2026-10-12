package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.LendItemReturn;
import com.example.demo.entity.LendReturn;

import java.util.List;

import java.util.Map;

/**
 * 还款 Service
 * 负责：满标放款、生成还款计划、借款人还款、投资人回款
 */
public interface LendReturnService extends IService<LendReturn> {

    /**
     * 发起满标放款（跳转旺旺银行）：
     * 校验标的满标待放款 → 组装银行放款参数+签名 → 返回自动提交表单HTML
     *
     * <p><b>注意：</b>旺旺银行（ww_bank-1.0.0.jar）的 {@code /userInvest/AgreeAccountLendProject}
     * 是<b>同步 JSON 接口</b>，不像绑卡/充值/提现/投标那样有异步回调。
     * 浏览器提交这张表单只会拿到一段 JSON，平台永远收不到 {@code notifyMakeLoan}，
     * 标的会永久卡在「已满标待放款」。因此管理端请改用 {@link #makeLoanSync(Long, Long)}，
     * 本方法仅作为「换成真银行时走页面跳转」的备用通道保留。
     *
     * @param lendId 标的ID
     * @return 跳转到银行页面的 HTML 表单串
     */
    String buildMakeLoanForm(Long lendId, Long adminId);

    /**
     * 同步满标放款（服务端直连银行，推荐）：
     * 校验 → 服务端 POST 银行放款接口 → 银行同步返回 {@code resultCode=0000} 表示已划转
     * → 平台随即落账（生成还款计划 + 放款流水 + 标的转还款中）。
     *
     * <p>资金仍然不经过平台账户：划转由银行 {@code UserInvestService.makeLoan()} 完成，
     * 平台只做记账与计划生成，与异步回调路径 {@link #handleMakeLoanNotify(Map)} 共用同一段落账逻辑。
     *
     * @param lendId  标的ID
     * @param adminId 操作管理员ID（留痕用）
     * @return {@code success=true} 表示银行已划转且平台已落账；{@code bankResponse} 为银行原始应答
     */
    Map<String, Object> makeLoanSync(Long lendId, Long adminId);

    /**
     * 处理银行放款异步回调：验签 → 状态/金额校验 → 幂等 → 生成还款计划 → 借款人入账 → 标的状态改还款中
     *
     * @param params 银行回调的全部参数（含 sign）
     * @return true=业务已受理（应答 success）；false=验签失败或数据异常（应答 fail）
     */
    boolean handleMakeLoanNotify(Map<String, Object> params);

    /**
     * 发起还款（跳转旺旺银行）：
     * 校验还款计划/借款人/绑卡 → 组装银行还款参数+签名 → 返回自动提交表单HTML
     * 借款人扣款不在本方法执行，由银行异步回调驱动（合规：资金不过平台账户）
     *
     * @param lendReturnId 还款计划ID
     * @param userId       借款人ID
     * @return 跳转到银行页面的 HTML 表单串
     */
    String buildRepayForm(Long lendReturnId, Long userId);

    /**
     * 处理银行还款异步回调：验签 → 状态/金额校验 → 幂等 → 扣借款人余额 → 投资人回款 → 更新计划状态 → 判断结清
     *
     * @param params 银行回调的全部参数（含 sign）
     * @return true=业务已受理（应答 success）；false=验签失败或数据异常（应答 fail）
     */
    boolean handleRepayNotify(Map<String, Object> params);

    /**
     * 借款人查询自己的还款计划
     */
    List<LendReturn> listByBorrower(Long userId);

    /**
     * 管理端：查询某标的的还款计划
     */
    List<LendReturn> listByLendId(Long lendId);

    /**
     * 查询某一期某投资人的收益明细（带归属校验）。
     *
     * <p>借款人可查看该期全部投资人明细（还款时需知道钱还给谁）；
     * 投资人只能查看自己那一条；与本期无关的用户查到空列表。</p>
     */
    List<LendItemReturn> listItemReturns(Long lendReturnId, Long userId);

    /**
     * 投资人查询自己的回款计划
     */
    List<LendItemReturn> listMyItemReturns(Long investUserId);

    /**
     * 逾期扫描：查找已过还款日但未还的还款计划
     * 标记逾期、按日计收罚息、标的置为逾期状态、给借款人发催收消息
     * 幂等：已逾期的只刷新罚息，不重复发送催收通知
     *
     * @return 本次处理的逾期记录数（含新标记 + 罚息刷新）
     */
    int overdueCheck();

}
