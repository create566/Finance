package com.wwfinance.common.exception;


import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResourceNotFoundException extends RuntimeException {


    //错误码
    private Integer code;
    //错误消息
    private String msg;


    public ResourceNotFoundException(Integer code, String msg) {
        // 必须回传给父类，否则 getMessage() 恒为 null，日志/前端都看不到真实业务原因
        super(msg);
        this.msg = msg;
        this.code = code;
    }
}