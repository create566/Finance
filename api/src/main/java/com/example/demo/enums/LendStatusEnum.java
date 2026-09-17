package com.example.demo.enums;

import lombok.Getter;

/**
 * 标的状态枚举
 * 对应 lend.status
 */
@Getter
public enum LendStatusEnum {

    PENDING(0, "待发布"),
    FUNDING(1, "募资中"),
    COMPLETED(2, "已满标待放款"),
    OVERDUE(3, "已逾期"),
    OFFLINE(4, "已下架"),
    REPAYING(5, "还款中"),
    SETTLED(6, "已完结"),
    FAILED(7, "已流标");

    private final Integer code;
    private final String desc;

    LendStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static LendStatusEnum fromCode(Integer code) {
        for (LendStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知标的状态码: " + code);
    }
}
