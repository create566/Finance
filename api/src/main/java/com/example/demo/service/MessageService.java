package com.example.demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.Message;
import com.example.demo.enums.MessageTypeEnum;

public interface MessageService extends IService<Message> {

    /**
     * 发送消息：先落库站内信，再走短信微服务通道（与验证码同通道）
     * 短信发送失败只记日志（有 fallback），不影响站内信和主业务
     *
     * @param userId      接收用户ID
     * @param type        消息类型
     * @param title       标题
     * @param content     内容（同时作为短信正文）
     * @return 站内消息记录
     */
    Message send(Long userId, MessageTypeEnum type, String title, String content);

    /**
     * 发送逾期催收通知
     */
    Message sendOverdueNotice(Long userId, String content);

    /** 发送注册成功通知 */
    default Message sendRegisterNotice(Long userId, String mobile) {
        return send(userId, MessageTypeEnum.REGISTER, "【注册成功】", "您的账户已注册成功，手机号：" + mobile);
    }

    /** 发送绑卡成功通知 */
    default Message sendBindNotice(Long userId, String bankCard) {
        return send(userId, MessageTypeEnum.BIND, "【绑卡成功】", "您的银行卡 " + bankCard + " 已绑定成功，可进行充值/提现操作");
    }

    /** 发送充值成功通知 */
    default Message sendRechargeNotice(Long userId, java.math.BigDecimal amount) {
        return send(userId, MessageTypeEnum.RECHARGE, "【充值成功】", "您的账户成功充值 " + amount + " 元");
    }

    /** 发送提现成功通知 */
    default Message sendWithdrawNotice(Long userId, java.math.BigDecimal amount) {
        return send(userId, MessageTypeEnum.WITHDRAW, "【提现成功】", "您的账户成功提现 " + amount + " 元");
    }

    /** 发送借款审批结果通知 */
    default Message sendBorrowApprovalNotice(Long userId, boolean approved, java.math.BigDecimal amount) {
        String content = approved
                ? "您的借款申请 " + amount + " 元已审批通过，已生成投资标的"
                : "您的借款申请 " + amount + " 元未通过审批，请核对资料后重新申请";
        return send(userId, MessageTypeEnum.BORROW_APPROVED, "【借款审批结果】", content);
    }

    /** 发送借款人实名认证结果通知 */
    default Message sendBorrowerAuthNotice(Long userId, boolean approved) {
        String content = approved
                ? "您的借款人实名认证已通过，现在可以提交借款申请"
                : "您的借款人实名认证未通过，请核对资料后重新提交";
        return send(userId, MessageTypeEnum.BORROWER_AUTH, "【实名认证结果】", content);
    }

    /** 发送投资人实名认证结果通知 */
    default Message sendInvestorAuthNotice(Long userId, boolean approved) {
        String content = approved
                ? "您的投资人实名认证已通过，现在可以正常投标"
                : "您的投资人实名认证未通过，请核对资料后重新提交";
        return send(userId, MessageTypeEnum.INVESTOR_AUTH, "【实名认证结果】", content);
    }

    /** 发送标的发布通知（给借款人） */
    default Message sendLendPublishNotice(Long userId, String lendNo, java.math.BigDecimal amount) {
        return send(userId, MessageTypeEnum.LEND_PUBLISH, "【标的已发布】",
                "您的借款标的 " + lendNo + "（金额 " + amount + " 元）已发布，正在募集中");
    }

    /** 发送投资成功通知（给投资人） */
    default Message sendInvestNotice(Long userId, java.math.BigDecimal amount, String lendTitle) {
        return send(userId, MessageTypeEnum.INVEST, "【投资成功】",
                "您成功投资 " + amount + " 元，标的：" + lendTitle);
    }

    /** 发送满标放款通知（给借款人） */
    default Message sendMakeLoanNotice(Long userId, String lendNo, java.math.BigDecimal amount) {
        return send(userId, MessageTypeEnum.MAKE_LOAN, "【满标放款】",
                "您的借款标的 " + lendNo + "（金额 " + amount + " 元）已满标放款，资金已入账，请按时还款");
    }

    /** 发送还款成功通知（给借款人） */
    default Message sendRepayNotice(Long userId, Integer currentPeriod, java.math.BigDecimal total) {
        return send(userId, MessageTypeEnum.REPAY, "【还款成功】",
                "您的第 " + currentPeriod + " 期还款 " + total + " 元已成功扣款，请关注后续还款计划");
    }

    /** 发送标的结清通知（给借款人）：表明该笔借款已全部还清，信用 +10 分 */
    default Message sendLendSettleNotice(Long userId, String lendNo,
                                         java.math.BigDecimal totalPrincipal,
                                         java.math.BigDecimal totalInterest) {
        return send(userId, MessageTypeEnum.LEND_SETTLE, "【标的结清】",
                "您的借款标的 " + lendNo + " 已全部还清，"
                        + "本金合计 " + totalPrincipal + " 元，利息合计 " + totalInterest
                        + " 元。感谢您按时还款，信用积分 +10。");
    }

    /** 发送标的流标通知（给借款人） */
    default Message sendLendFailNotice(Long userId, String lendNo, java.math.BigDecimal amount) {
        return send(userId, MessageTypeEnum.LEND_FAIL, "【标的流标】",
                "您的借款标的 " + lendNo + "（金额 " + amount + " 元）因募集超时已流标，资金已退回投资人账户");
    }

    /** 发送投资回款到账通知（给投资人） */
    default Message sendInvestReturnNotice(Long userId, Integer period, java.math.BigDecimal total,
                                           String lendTitle) {
        return send(userId, MessageTypeEnum.INVEST_RETURN, "【投资回款到账】",
                "您投资的标的「" + lendTitle + "」第 " + period + " 期回款 " + total
                        + " 元已到账，请查收");
    }

    /** 发送合同签署成功通知 */
    default Message sendContractSignNotice(Long userId, String contractTypeText, String contractNo) {
        return send(userId, MessageTypeEnum.CONTRACT_SIGN, "【合同签署成功】",
                "您已成功签署《" + contractTypeText + "》，合同编号：" + contractNo
                        + "。可前往「我的合同」查看正文与签署存证。");
    }

    /** 发送撤标成功通知（给投资人，资金已解冻） */
    default Message sendInvestCancelNotice(Long userId, java.math.BigDecimal amount, String lendTitle) {
        return send(userId, MessageTypeEnum.INVEST_CANCEL, "【撤标成功】",
                "您对标的「" + lendTitle + "」的投资 " + amount + " 元已撤销，冻结资金已解冻");
    }

    /** 发送标的已下架通知（给借款人） */
    default Message sendLendOfflineNotice(Long userId, String lendNo, java.math.BigDecimal amount) {
        return send(userId, MessageTypeEnum.LEND_OFFLINE, "【标的已下架】",
                "您的借款标的 " + lendNo + "（金额 " + amount + " 元）已被平台下架，不再对外募集。"
                        + "若已产生投资，对应冻结资金将原路解冻退回投资人。");
    }

    /** 发送标的已下架、投资资金已解冻通知（给投资人） */
    default Message sendLendOfflineRefundNotice(Long userId, java.math.BigDecimal amount,
                                                String lendTitle) {
        return send(userId, MessageTypeEnum.LEND_OFFLINE, "【标的已下架】",
                "您投资的标的「" + lendTitle + "」已被平台下架，您的投资 " + amount
                        + " 元已解冻退回账户");
    }

    /**
     * 发送"标的已满标、待借款人签署《放款确认书》"通知（给借款人）。
     *
     * <p>合规要点：投资人资金处于冻结状态，若借款人不签《放款确认书》则既不能放款也不能退回，
     * 会形成资金无限期悬停（资金池风险）。此处主动告知借款人尽快签署，是"资金不悬停"的事中抓手。</p>
     */
    default Message sendMakeLoanPendingNotice(Long userId, String lendNo, java.math.BigDecimal amount) {
        return send(userId, MessageTypeEnum.MAKE_LOAN_PENDING, "【满标待签署】",
                "您的借款标的 " + lendNo + "（金额 " + amount + " 元）已满标，"
                        + "请尽快签署《放款确认书》以便放款。逾期未签署的，平台将按约定退还投资人资金。");
    }

    /**
     * 发送"超期未签《放款确认书》、系统已终止撮合"通知（给借款人）。
     *
     * <p>文案必须点明是"借款人自己逾期未签"导致的，而不是笼统的"平台下架" ——
     * 处置依据来自《借款合同》里事先约定的超期条款，告知义务要做到位。</p>
     */
    default Message sendMakeLoanTimeoutNoticeToBorrower(Long userId, String lendNo,
                                                        java.math.BigDecimal amount) {
        return send(userId, MessageTypeEnum.MAKE_LOAN_TIMEOUT, "【超期未签，已终止】",
                "您的借款标的 " + lendNo + "（金额 " + amount + " 元）已满标，"
                        + "但您未在约定时限内签署《放款确认书》。平台已按合同约定终止本次借贷，"
                        + "投资人的冻结资金将原路退回。");
    }

    /**
     * 发送"因借款人超期未签，投资资金已退回"通知（给投资人）。
     *
     * <p>对投资人来说这跟"平台下架"是完全不同的两件事，用 LEND_OFFLINE(29) 会导致
     * 用户莫名其妙：钱退回来了，却收到一条"标的已下架"。故独立成 MAKE_LOAN_TIMEOUT(31)。</p>
     */
    default Message sendMakeLoanTimeoutNoticeToInvestor(Long userId, java.math.BigDecimal amount,
                                                        String lendTitle) {
        return send(userId, MessageTypeEnum.MAKE_LOAN_TIMEOUT, "【超期未签，已退款】",
                "您投资的标的「" + lendTitle + "」因借款人超期未签署《放款确认书》已终止，"
                        + "您的投资 " + amount + " 元已解冻退回账户");
    }

    /** 发送提现失败通知 */
    default Message sendWithdrawFailNotice(Long userId, java.math.BigDecimal amount) {
        return send(userId, MessageTypeEnum.WITHDRAW_FAIL, "【提现失败】",
                "您的提现 " + amount + " 元未成功，资金仍在您的账户中，请检查银行卡状态后重试");
    }

    /** 发送充值失败通知 */
    default Message sendRechargeFailNotice(Long userId, java.math.BigDecimal amount) {
        return send(userId, MessageTypeEnum.RECHARGE_FAIL, "【充值失败】",
                "您的充值 " + amount + " 元未成功，请确认银行卡状态后重试");
    }

    /**
     * 分页查询某用户的消息
     */
    Page<Message> pageByUser(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 未读消息数
     */
    long countUnread(Long userId);

    /**
     * 标记已读（校验消息归属）
     */
    boolean markRead(Long messageId, Long userId);
}
