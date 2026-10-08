package com.example.demo.enums;

import lombok.Getter;

/**
 * 站内消息类型枚举
 */
@Getter
public enum MessageTypeEnum {

    OVERDUE(1, "逾期催收"),
    SYSTEM(2, "系统通知"),
    AUDIT(3, "审批通知"),

    // 业务通知类型
    REGISTER(11, "注册成功"),
    BIND(12, "绑卡成功"),
    RECHARGE(13, "充值成功"),
    WITHDRAW(14, "提现成功"),
    BORROW_APPROVED(15, "借款审批"),
    BORROWER_AUTH(16, "实名认证"),
    LEND_PUBLISH(17, "标的发布"),
    INVEST(18, "投资成功"),
    MAKE_LOAN(19, "满标放款"),
    REPAY(20, "还款成功"),
    LEND_SETTLE(21, "标的结清"),
    LEND_FAIL(22, "标的流标"),

    // 资金与合同类通知（补齐"操作发生了但消息中心没动静"的缺口）
    INVEST_RETURN(23, "投资回款"),
    CONTRACT_SIGN(24, "合同签署"),
    INVEST_CANCEL(25, "撤标"),
    WITHDRAW_FAIL(26, "提现失败"),
    RECHARGE_FAIL(27, "充值失败"),
    INVESTOR_AUTH(28, "投资人认证"),

    // 标的生命周期补漏：下架与流标一样会退还投资人资金，但此前下架后双方都收不到任何消息
    LEND_OFFLINE(29, "标的已下架"),

    // 资金安全补漏：满标后借款人未签《放款确认书》会卡在"满标待放款"，
    // 投资人冻结资金既不能放款也不能退回 —— 必须主动催签，避免资金无限期悬停
    MAKE_LOAN_PENDING(30, "满标待签确认书"),

    // 资金安全闭环：满标后借款人超期未签《放款确认书》，定时任务自动终止撮合并退还投资人资金。
    // 与 LEND_OFFLINE(29) 区分开：29 是人工下架，31 是系统按合同条款自动处置，
    // 对投资人的说法完全不同（一个是"平台下架了"，一个是"借款人逾期未签，钱退给你"）。
    MAKE_LOAN_TIMEOUT(31, "超期未签已退款");

    private final Integer code;
    private final String desc;

    MessageTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
