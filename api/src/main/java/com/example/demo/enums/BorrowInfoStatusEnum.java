package com.example.demo.enums;

import lombok.Getter;

/**
 * 借款申请状态枚举
 * 对应 borrow_info.status
 */
@Getter
public enum BorrowInfoStatusEnum {

    DRAFT(0, "草稿"),
    AUDITING(1, "审核中"),
    APPROVED(2, "审核通过"),
    REJECTED(-1, "审核拒绝");

    private final Integer code;
    private final String desc;

    BorrowInfoStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static BorrowInfoStatusEnum fromCode(Integer code) {
        for (BorrowInfoStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知借款状态码: " + code);
    }
}
