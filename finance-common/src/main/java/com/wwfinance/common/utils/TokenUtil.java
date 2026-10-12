package com.wwfinance.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * token 辅助类。
 *
 * <p><b>2026-10-08 抽取到 finance-common</b>：此前 api（{@code com.example.demo.utils.TokenUtil}）
 * 与 finance-oss（{@code com.wwfinance.oss.utils.TokenUtil}）各有一份；oss 那份是 api 的真子集，
 * 解析规则必须与 api 完全一致，否则同一张 token 两边一个认一个不认。现在合并为一份。</p>
 *
 * <p>解析规则（改一处即对两端同时生效）：</p>
 * <ul>
 *   <li>token 带固定前缀 {@code "5grcs "}，解析前先剥掉；</li>
 *   <li>签名算法 HS512，密钥取自 {@code security.jwt.secret}（由 {@code JwtSecretConfig} 注入）；</li>
 *   <li>claims：{@code token_userid} / {@code token_role} / {@code token_username}
 *       / {@code token_admin_role}（最后一个是 2026-10-09 P2-11 新增的细粒度后台角色）。</li>
 * </ul>
 */
public class TokenUtil {

    /**
     * 加解密密匙。
     *
     * <p>不再硬编码：值由 {@code com.wwfinance.common.config.JwtSecretConfig} 在启动时从
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
        if (token == null || !token.startsWith(tokenHead)) {
            return true;
        }
        token = token.substring(tokenHead.length());
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
     *
     * @deprecated 2026-10-09（P2-11）：请改用三参版本显式传入后台角色。
     * 本方法保留只为兼容既有测试与调用点，等价于把角色固定为
     * {@link com.wwfinance.common.auth.AdminRoles#ADMIN}（<b>最小权限</b>）。
     */
    @Deprecated
    public static String generateAdminToken(String adminId, String username) {
        return generateAdminToken(adminId, username, com.wwfinance.common.auth.AdminRoles.DEFAULT);
    }

    /**
     * 生成管理员token（带 role=admin + 细粒度后台角色）。
     *
     * <p><b>2026-10-09 新增（P2-11 权限分级）</b>：原先 {@code token_role} 被写死成
     * {@code "admin"}，任何后台账号都是同一个角色，权限无法分级。现在拆成两个 claim：</p>
     * <ul>
     *   <li>{@code token_role} —— <b>保持 {@code "admin"} 不变</b>。它是命名空间判别位，
     *       决定 Redis 会话键走 {@code login:token:admin:<id>}；一旦改成 SUPER_ADMIN，
     *       {@code JwtAuthInterceptor} 会去 {@code login:token:user:<id>} 找会话，
     *       <b>全体管理员立刻掉线</b>。</li>
     *   <li>{@code token_admin_role} —— 本次新增的细粒度角色（见
     *       {@link com.wwfinance.common.auth.AdminRoles}），只用于权限判定。</li>
     * </ul>
     *
     * @param adminId   管理员id
     * @param username  管理员账号
     * @param adminRole 后台角色，取值 {@link com.wwfinance.common.auth.AdminRoles}；
     *                  传 null / 空 时按最小权限 {@code ADMIN} 处理
     */
    public static String generateAdminToken(String adminId, String username, String adminRole) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("token_userid", adminId);
        claims.put("token_username", username);
        // 命名空间判别位：必须恒为 "admin"，不要写成细粒度角色
        claims.put("token_role", "admin");
        claims.put("token_admin_role", com.wwfinance.common.auth.AdminRoles.normalize(adminRole));
        return tokenHead + localGenerateToken(claims);
    }


    /**
     * 获取token信息
     *  用户类型
     */
    public static Map<String, String> getMapInfoFromToken(String token) {
        if (token == null || !token.startsWith(tokenHead)) {
            return new HashMap<>();
        }
        token = token.substring(tokenHead.length());
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return new HashMap<>();
        }
        Map<String, String> tokenInfoMap = new HashMap<>();
        Object userid = claims.get("token_userid");
        if (userid != null) {
            tokenInfoMap.put("token_userid", userid.toString());
        }
        Object role = claims.get("token_role");
        if (role != null) {
            tokenInfoMap.put("token_role", role.toString());
        }
        Object username = claims.get("token_username");
        if (username != null) {
            tokenInfoMap.put("token_username", username.toString());
        }
        // 细粒度后台角色（P2-11）。老 token 没有这个 claim，取不到时由调用方按最小权限兜底。
        Object adminRole = claims.get("token_admin_role");
        if (adminRole != null) {
            tokenInfoMap.put("token_admin_role", adminRole.toString());
        }
        return tokenInfoMap;
    }
}
