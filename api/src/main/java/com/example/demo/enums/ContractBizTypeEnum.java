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
    LEND_ITEM("LEND_ITEM", "投资记录"),
    /**
     * 关联用户 user.id —— 用户级一次性协议（《居间服务协议》《资金存管协议》）。
     * <p>
     * 这类协议不挂在任何标的上，全平台一个用户只有一份：{@code biz_id = userId}，
     * 合同金额固定传 {@code 0}（{@code uk_biz} 含 amount 列，若传 NULL 会因
     * MySQL 唯一键对 NULL 不去重而产生重复行）。
     */
    USER("USER", "用户");

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
