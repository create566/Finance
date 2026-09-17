package com.example.demo.enums;

import lombok.Getter;

/**
 * 用户类型枚举
 * 对应 user.user_type：1=投资者，2=借款人
 * （实名认证通过后用户被置为借款人，见 BorrowerServiceImpl.approveAuth）
 */
@Getter
public enum UserTypeEnum {

    INVESTOR(1, "投资者"),
    BORROWER(2, "借款人");

    private final Integer code;
    private final String desc;

    UserTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserTypeEnum fromCode(Integer code) {
        for (UserTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知用户类型码: " + code);
    }
}
