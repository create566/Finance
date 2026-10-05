package com.example.demo.enums;

import lombok.Getter;

/**
 * 交易流水状态枚举
 * 对应 trans_flow.status
 *
 * 只有「充值 / 提现」会出现 PROCESSING 态：发起时先落一条处理中流水，
 * 等银行异步回调再置为 SUCCESS / FAILED，这样用户点完立刻能在资金流水里看到这笔单。
 * 其余类型（投标/回款/放款/还款）由银行回调驱动，落库即终态。
 */
@Getter
public enum TransStatusEnum {

    PROCESSING(0, "处理中"),
    SUCCESS(1, "成功"),
    FAILED(2, "失败");

    private final Integer code;
    private final String desc;

    TransStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String descOf(Integer code) {
        if (code == null) {
            return "";
        }
        for (TransStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status.desc;
            }
        }
        return "未知";
    }
}
