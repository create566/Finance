package com.example.demo.enums;

import java.util.Arrays;

/**
 * 合同类型
 */
public enum ContractTypeEnum {

    /** 借款合同：借款人与平台签署，标的发布募资前必须签 */
    LOAN("LOAN", "借款合同", "TPL_LOAN"),
    /** 出借协议（投资合同）：投资人在投资下单前必须签 */
    INVEST("INVEST", "出借协议", "TPL_INVEST"),
    /** 放款确认书：借款人在满标放款前必须签 */
    PAYMENT("PAYMENT", "放款确认书", "TPL_PAYMENT");

    private final String code;
    private final String desc;
    private final String defaultTemplateNo;

    ContractTypeEnum(String code, String desc, String defaultTemplateNo) {
        this.code = code;
        this.desc = desc;
        this.defaultTemplateNo = defaultTemplateNo;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public String getDefaultTemplateNo() {
        return defaultTemplateNo;
    }

    public static ContractTypeEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
