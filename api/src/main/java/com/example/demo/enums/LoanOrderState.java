package com.example.demo.enums;

import lombok.Getter;

// 1. 借款订单状态枚举
@Getter
public enum LoanOrderState {
    DRAFT(0, "草稿"),
    WAIT_APPROVAL(1, "待审核"),
    APPROVED(2, "审核通过"),
    WAIT_SIGN(3, "待签约"),
    WAIT_DISBURSEMENT(4, "待放款"),
    REPAYING(5, "还款中"),
    COMPLETED(6, "已完结"),
    REJECTED(-1, "已拒贷"),
    OVERDUE(7, "已逾期");

    private final Integer code;
    private final String desc;

    LoanOrderState(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // 根据 code 获取枚举
    public static LoanOrderState fromCode(Integer code) {
        for (LoanOrderState state : values()) {
            if (state.code.equals(code)) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知状态码: " + code);
    }

    /*
    *核心方法：判断当前状态是否可以转换到目标状态
     */
    public boolean canTransitionTo(LoanOrderState target){
        switch (this) {
            case DRAFT:
                return target == WAIT_APPROVAL;

            case WAIT_APPROVAL:
                return target == APPROVED || target == REJECTED;

            case APPROVED:
                return target == WAIT_SIGN || target == REJECTED;

            case WAIT_SIGN:
                return target == WAIT_DISBURSEMENT || target == REJECTED;

            case WAIT_DISBURSEMENT:
                return target == REPAYING || target == REJECTED;

            case REPAYING:
                // 还款中只能变成已完结（正常结清）或已逾期（系统触发）
                return target == COMPLETED || target == OVERDUE;

            // 以下状态为“终态”，不允许再发生任何流转
            case COMPLETED:
            case REJECTED:
            case OVERDUE:
                return false;

            default:
                return false;
        }
    }


}
