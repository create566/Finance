package com.example.demo.utils;

/**
 * 当前登录用户上下文（线程级）
 * 拦截器在 preHandle 中写入，Controller/Service 在业务中读取，afterCompletion 中清除
 */
public class LoginUserContext {

    private static final ThreadLocal<Integer> CURRENT_USERID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USERNAME = new ThreadLocal<>();


    public static Integer getUserid() {
        return CURRENT_USERID.get();
    }

    public static void setUserid(Integer userid) {
        CURRENT_USERID.set(userid);
    }

    public static String getRole() {
        return CURRENT_ROLE.get();
    }

    public static void setRole(String role) {
        CURRENT_ROLE.set(role);
    }

    public static String getUsername() {
        return CURRENT_USERNAME.get();
    }

    public static void setUsername(String username) {
        CURRENT_USERNAME.set(username);
    }

    public static boolean isAdmin() {
        return "admin".equals(CURRENT_ROLE.get());
    }

    public static void clear() {
        CURRENT_USERID.remove();
        CURRENT_ROLE.remove();
        CURRENT_USERNAME.remove();
    }
}
