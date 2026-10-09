package com.example.demo.enums;

import java.util.Arrays;

/**
 * 合同类型
 * <p>
 * 三类核心合同（P9-60/P9-61 补齐后共 5 类）：
 * <ul>
 *   <li>{@link #LOAN} 借款合同 —— <b>甲方=借款人</b>，乙方=全体出借人（以各《出借协议》为准）。
 *       历史上注释写作"借款人与平台签署"、且不落乙方，与正文口径矛盾（P9-58 已修正正文）。</li>
 *   <li>{@link #INVEST} 出借协议 —— <b>唯一的双边合同</b>：甲方=投资人、乙方=借款人，
 *       按投资笔数每人一份、金额与投资额一致。</li>
 *   <li>{@link #PAYMENT} 放款确认书 —— 甲方=借款人，放款前必签。</li>
 *   <li>{@link #SERVICE} 居间服务协议 —— <b>用户级一次性</b>：甲方=用户、乙方=平台
 *       （居间方，收取撮合服务费的合同依据）。</li>
 *   <li>{@link #DEPOSIT} 资金存管协议 —— <b>用户级一次性</b>：甲方=用户、乙方=存管银行。</li>
 * </ul>
 * ⚠️ 用户级协议（SERVICE / DEPOSIT）不挂在标的上，`biz_type=USER`、`biz_id=userId`、`amount=0`。
 */
public enum ContractTypeEnum {

    /** 借款合同：甲方=借款人（乙方为全体出借人，party_b_id 留空，以《出借协议》为明细） */
    LOAN("LOAN", "借款合同", "TPL_LOAN"),
    /** 出借协议（投资合同）：甲方=投资人、乙方=借款人，投资下单前必须签 */
    INVEST("INVEST", "出借协议", "TPL_INVEST"),
    /** 放款确认书：甲方=借款人，满标放款前必须签 */
    PAYMENT("PAYMENT", "放款确认书", "TPL_PAYMENT"),
    /** 居间服务协议：甲方=用户、乙方=旺旺金融平台（平台↔借款人 / 平台↔投资人，用户级一次） */
    SERVICE("SERVICE", "居间服务协议", "TPL_SERVICE"),
    /** 资金存管协议：甲方=用户、乙方=存管银行（用户级一次） */
    DEPOSIT("DEPOSIT", "资金存管协议", "TPL_DEPOSIT");

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
