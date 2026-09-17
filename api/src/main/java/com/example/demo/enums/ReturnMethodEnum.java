package com.example.demo.enums;

import lombok.Getter;

/**
 * 还款方式枚举
 */
@Getter
public enum ReturnMethodEnum {

    EQUAL_PRINCIPAL_INTEREST(1, "等额本息"),
    EQUAL_PRINCIPAL(2, "等额本金"),
    MONTHLY_INTEREST_LUMP_PRINCIPAL(3, "每月还息到期还本"),
    LUMP_SUM(4, "一次还本付息");

    private final Integer code;
    private final String desc;

    ReturnMethodEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ReturnMethodEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ReturnMethodEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
