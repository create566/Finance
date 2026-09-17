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
    LEND_FAIL(22, "标的流标");

    private final Integer code;
    private final String desc;

    MessageTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
