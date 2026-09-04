package com.wwfinance.common.utils;

import java.util.Random;

public class SmsCodeUtil {

    public static String generateCode(int length) {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    public static String generateCode() {
        return generateCode(6);
    }
}