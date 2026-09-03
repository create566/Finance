package com.kzip.app.controller;

import lombok.Data;
import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    // 状态码，比如 200 成功，500 失败

    private Integer code;
    // 提示信息，比如 "操作成功" 或 "用户名不能为空"
    private String msg;
    // 具体的数据，泛型表示可以是任意对象
    private T data;

    // 构造方法私有，不允许外部直接 new，必须通过静态方法调用
    private Result() {}

    // ============ 成功的静态方法 ============
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200); // 约定成功为 200
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success(String msg, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }

    // ============ 失败的静态方法 ============
    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(null);
        return result;
    }

    // ============ 失败的静态方法 ============
    public static <T> Result<T> fail( String msg) {
        Result<T> result = new Result<>();
        result.setCode(400);
        result.setMsg(msg);
        result.setData(null);
        return result;
    }





    public static <T> Result<T> error(String msg) {
        return error(500, msg); // 默认用 500 代表系统异常
    }
}