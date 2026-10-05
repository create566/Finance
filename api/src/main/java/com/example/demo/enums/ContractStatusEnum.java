package com.example.demo.enums;

/**
 * 合同状态
 */
public enum ContractStatusEnum {

    /** 待签署 */
    PENDING(0, "待签署"),
    /** 已签署 */
    SIGNED(1, "已签署"),
    /** 已作废 */
    REVOKED(2, "已作废");

    private final Integer code;
    private final String desc;

    ContractStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static ContractStatusEnum fromCode(Integer code) {
        for (ContractStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
