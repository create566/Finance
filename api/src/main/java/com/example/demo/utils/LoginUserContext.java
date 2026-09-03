package com.example.demo.utils;

/**
 * 当前登录用户上下文（线程级）
 * 拦截器在 preHandle 中写入，Controller/Service 在业务中读取，afterCompletion 中清除
 */
public class LoginUserContext {

    private static final ThreadLocal<Integer> CURRENT_USERID = new ThreadLocal<>();


    public static Integer getUserid() {
      return  CURRENT_USERID.get();
    }
    public static void setUserid(Integer userid) {
        CURRENT_USERID.set(userid);
    }

    public static void clear() {
        CURRENT_USERID.remove();
    }
}
