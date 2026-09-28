package com.wwfinance.common.utils;

import java.security.SecureRandom;

public class SmsCodeUtil {

    // 验证码必须用密码学安全随机数：java.util.Random 是线性同余伪随机，
    // 可从少量输出推测后续序列，短信验证码场景不可接受
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String generateCode(int length) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(SECURE_RANDOM.nextInt(10));
        }
        return code.toString();
    }

    public static String generateCode() {
        return generateCode(6);
    }
}