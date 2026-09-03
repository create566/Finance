package com.example.demo.enums.event;

public enum LoanOrderEvent {
    SUBMIT,     // 提交审核
    APPROVE,    // 审核通过
    REJECT,     // 审核拒绝
    SIGN,       // 签署合同
    DISBURSE,   // 放款
    REPAY,      // 还款
    OVERDUE     // 逾期
}
