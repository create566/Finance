package com.example.demo.enums;

import java.util.Arrays;

/**
 * 合同关联的业务对象类型
 * <p>
 * 与 {@link ContractTypeEnum} 组合成唯一键（uk_biz），保证同一业务的同一类合同只有一份。
 */
public enum ContractBizTypeEnum {

    /** 关联标的 lend.id */
    LEND("LEND", "标的"),
    /** 关联借款申请 borrow_info.id */
    BORROW_INFO("BORROW_INFO", "借款申请"),
    /** 关联投资记录 lend_item.id */
    LEND_ITEM("LEND_ITEM", "投资记录");

    private final String code;
    private final String desc;

    ContractBizTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static ContractBizTypeEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
