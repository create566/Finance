package com.example.demo.enums;

import lombok.Getter;

/**
 * 银行卡绑定状态枚举
 * 对应 user_bind.status 与 user.bind_status
 */
@Getter
public enum BindStatusEnum {

    UNBOUND(0, "未绑定"),
    BOUND(1, "已绑定");

    private final Integer code;
    private final String desc;

    BindStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static BindStatusEnum fromCode(Integer code) {
        for (BindStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知绑卡状态码: " + code);
    }
}
