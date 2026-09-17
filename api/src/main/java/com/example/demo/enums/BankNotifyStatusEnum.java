package com.example.demo.enums;

import lombok.Getter;

/**
 * 旺旺银行回调结果状态枚举
 * 银行异步通知参数 status：1=成功，其他=失败/处理中
 */
@Getter
public enum BankNotifyStatusEnum {

    SUCCESS("1", "成功");

    private final String code;
    private final String desc;

    BankNotifyStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 判断银行回调是否为成功状态
     */
    public static boolean isSuccess(String status) {
        return SUCCESS.code.equals(status);
    }
}
