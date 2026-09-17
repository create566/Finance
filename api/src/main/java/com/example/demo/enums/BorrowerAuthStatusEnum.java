package com.example.demo.enums;

import lombok.Getter;

/**
 * 实名认证（借款人认证）状态枚举
 * 对应 user.borrow_auth_status 与 borrower.status
 */
@Getter
public enum BorrowerAuthStatusEnum {

    UNAUTH(0, "未认证"),
    AUTHING(1, "认证中"),
    AUTHED(2, "认证通过"),
    AUTH_FAIL(-1, "认证失败");

    private final Integer code;
    private final String desc;

    BorrowerAuthStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static BorrowerAuthStatusEnum fromCode(Integer code) {
        for (BorrowerAuthStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知认证状态码: " + code);
    }
}
