package com.example.demo.enums;

import lombok.Getter;

/**
 * 交易流水类型枚举
 * 对应 trans_flow.trans_type
 */
@Getter
public enum TransTypeEnum {

    RECHARGE(1, "充值"),
    WITHDRAW(2, "提现"),
    INVEST(3, "投标"),
    RETURN(4, "投资回款"),
    LOAN(5, "放款"),
    REPAY(6, "还款");

    private final Integer code;
    private final String desc;

    TransTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static TransTypeEnum fromCode(Integer code) {
        for (TransTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知交易类型码: " + code);
    }
}
