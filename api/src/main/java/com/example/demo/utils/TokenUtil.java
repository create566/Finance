package com.example.demo.utils;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * token辅助类
 */
public class TokenUtil {

    /**
     * 加解密密匙。
     *
     * <p>不再硬编码：值由 {@code config.JwtSecretConfig} 在启动时从
     * {@code security.jwt.secret} 注入。默认值刻意与历史硬编码保持一致 ——
     * 若改错会导致所有已签发 token 验签失败、全体在线用户掉线。</p>
     */
    private static String secret = "vsofo-5grcs-secret";

    private final static long expiration = 604800; // 过期时间/秒
    private final static String tokenHead = "5grcs "; // #JWT负载中拿到开头

    /** 供 Spring 启动时注入配置值（本类是静态工具类，无法直接 @Value 注入） */
    public static void setSecret(String value) {
        if (value != null && !value.trim().isEmpty()) {
            secret = value.trim();
        }
    }

    private static String localGenerateToken(Map<String, Object> claims) {
        Date expirationTime = new Date(System.currentTimeMillis() + expiration * 1000);
        return Jwts.builder().setClaims(claims).setExpiration(expirationTime).signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    private static Claims getClaimsFromToken(String token) {
        Claims claims = null;
        try {
            claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
        } catch (Exception e) {
            // 解析失败（过期/伪造/格式错误）统一返回 null，由调用方处理
        }
        return claims;
    }

    /**
     * token是否失效
     *
     * @return true 失效 false 未失效
     */
    public static boolean isTokenExpired(String token) {
        if (token.startsWith(tokenHead)) {
            token = token.substring(tokenHead.length());
        } else {
            return true;
        }
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return true;
        }

        return false;
    }

    /**
     * 生成用户token
     * @param token_userid  用户userid
     */
    public static String generateMerchantToken( String token_userid
    ) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("token_userid", token_userid);
        return tokenHead + localGenerateToken(claims);
    }

    /**
     * 生成管理员token（带 role=admin）
     * @param adminId  管理员id
     * @param username 管理员账号
     */
    public static String generateAdminToken(String adminId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("token_userid", adminId);
        claims.put("token_username", username);
        claims.put("token_role", "admin");
        return tokenHead + localGenerateToken(claims);
    }


    /**
     * 获取token信息
     *  用户类型
     */
    public static Map<String, String> getMapInfoFromToken(String token) {
        if (token.startsWith(tokenHead)) {
            token = token.substring(tokenHead.length());
        } else {
            return new HashMap<>();
        }
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return new HashMap<>();
        }
        Map<String, String> tokenInfoMap = new HashMap<>();
        tokenInfoMap.put("token_userid", claims.get("token_userid").toString());
        Object role = claims.get("token_role");
        if (role != null) {
            tokenInfoMap.put("token_role", role.toString());
        }
        Object username = claims.get("token_username");
        if (username != null) {
            tokenInfoMap.put("token_username", username.toString());
        }
        return tokenInfoMap;
    }
}

