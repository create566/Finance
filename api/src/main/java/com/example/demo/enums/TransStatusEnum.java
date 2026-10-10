package com.example.demo.enums;

import lombok.Getter;

/**
 * 交易流水状态枚举
 * 对应 trans_flow.status
 *
 * 「充值 / 提现 / 投标 / 还款」都会出现 PROCESSING 态：它们都在生成银行收银台表单的那一刻
 * 先落一条处理中流水（P0-1 占位：占住可用额、防止并发重复扣款），等银行异步回调再置为
 * SUCCESS / FAILED。其余类型（投资回款 / 放款）由银行回调驱动，落库即终态。
 *
 * ⚠️ FAILED 不代表"这笔彻底没了"，也不等于"已入账"：超时兜底任务
 * （{@code FinanceJobHandler#releaseStalePendingFlows}）会把用户放弃的占位置为 FAILED
 * 以释放可用额，因此回调幂等判据只看 SUCCESS（{@code existsSucceededByTransNo}）。
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
