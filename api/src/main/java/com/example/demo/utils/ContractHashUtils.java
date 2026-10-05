package com.example.demo.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 合同正文摘要工具
 * <p>
 * 合同正文的 SHA-256 摘要必须**只有一处实现**：签署时写入存证、事后校验时重算，
 * 两边算法若漂移就会把正常合同误判成被篡改。所以统一收在这里。
 */
public final class ContractHashUtils {

    private ContractHashUtils() {
    }

    public static String sha256(String text) {
        if (text == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
