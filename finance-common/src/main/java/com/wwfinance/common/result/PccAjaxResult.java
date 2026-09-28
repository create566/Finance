package com.wwfinance.common.result;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

// 注意：不能用 @Data——它会给空字段集合生成忽略 super 的 equals/hashCode，
// 导致内容完全不同的两个结果对象相等/哈希相同；继承 HashMap 只需 getter/setter
@Getter
@Setter
public class PccAjaxResult extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    /**
     * 状态码
     */
    public static final String CODE_TAG = "code";

    /**
     * 返回内容
     */
    public static final String MSG_TAG = "msg";


    /**
     * 数据对象
     */
    public static final String DATA_TAG = "data";


    /**
     * 初始化一个新创建的 AjaxResult 对象，使其表示一个空消息。
     */
    public PccAjaxResult() {
    }

    /**
     * 初始化一个新创建的 AjaxResult 对象
     *
     * @param code 状态码
     * @param msg  返回内容
     */
    public PccAjaxResult(int code, String msg) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
    }

    /**
     * 初始化一个新创建的 AjaxResult 对象
     *
     * @param code 状态码
     * @param msg  返回内容
     * @param data 数据对象
     */
    public PccAjaxResult(int code, String msg, Object data) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
        if (data != null) {
            super.put(DATA_TAG, data);
        }
    }

    /**
     * 方便链式调用
     *
     * @param key
     * @param value
     * @return
     */
    @Override
    public PccAjaxResult put(String key, Object value) {
        super.put(key, value);
        return this;
    }



    /**
     * 返回成功消息
     *
     * @param msg  返回内容
     * @param data 数据对象
     * @return 成功消息
     */
    public static PccAjaxResult success(int code, String msg, Object data) {
        return new PccAjaxResult(code, msg, data);
    }



    /**
     * 返回错误消息
     *
     * @param msg  返回内容
     * @param data 数据对象
     * @return 警告消息
     */
    public static PccAjaxResult error(int code,String msg, Object data) {
        return new PccAjaxResult(code, msg, data);
    }


}
