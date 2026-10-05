package com.example.demo.enums;

import lombok.Getter;

/**
 * 投资人实名认证状态枚举（与借款人认证状态值保持一致）
 */
@Getter
public enum InvestorAuthStatusEnum {

    UNAUTH(0, "未认证"),
    AUTHING(1, "认证中"),
    AUTHED(2, "认证通过"),
    AUTH_FAIL(-1, "认证失败");

    private final Integer code;
    private final String desc;

    InvestorAuthStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
