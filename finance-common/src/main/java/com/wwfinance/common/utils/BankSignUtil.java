package com.wwfinance.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class BankSignUtil {

    public static final String SIGN_KEY = System.getenv().getOrDefault("BANK_SIGN_KEY", "9876543210");

    public static String getSign(Map<String, Object> paramMap) {
        if (paramMap.containsKey("sign")) {
            paramMap.remove("sign");
        }
        TreeMap<String, Object> sorted = new TreeMap<>(paramMap);
        StringBuilder str = new StringBuilder();
        for (Map.Entry<String, Object> param : sorted.entrySet()) {
            str.append(param.getValue()).append("|");
        }
        str.append(SIGN_KEY);
        log.info("加密前：" + str.toString());
        String md5Str = MD5.encrypt(str.toString());
        log.info("加密后：" + md5Str);
        return md5Str;
    }

    public static boolean isSignEquals(Map<String, Object> paramMap) {
        String sign = (String) paramMap.get("sign");
        if (sign == null) {
            return false;
        }
        String md5Str = getSign(paramMap);
        if (!sign.equals(md5Str)) {
            return false;
        }
        return true;
    }

    public static Map<String, Object> switchMap(Map<String, String[]> paramMap) {
        Map<String, Object> resultMap = new java.util.HashMap<>();
        for (Map.Entry<String, String[]> param : paramMap.entrySet()) {
            resultMap.put(param.getKey(), param.getValue()[0]);
        }
        return resultMap;
    }

    public static String getPostData(Map<String, Object> paramMap) {
        TreeMap<String, Object> sorted = new TreeMap<>(paramMap);
        StringBuilder str = new StringBuilder();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            str.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        str.append(getSign(paramMap));
        return str.toString();
    }
}