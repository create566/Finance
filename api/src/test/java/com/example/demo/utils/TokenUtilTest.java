package com.example.demo.utils;

import com.wwfinance.common.utils.TokenUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 工具类测试，核心目的是<b>锁死密钥兼容性</b>。
 *
 * <p>本次改动把 secret 从硬编码改为配置注入。若默认值与历史值不一致，
 * 所有已签发 token 会验签失败，<b>全体在线用户当场掉线</b>。
 * 下面第一个用例就是防止这种事发生的护栏。</p>
 */
@DisplayName("JWT 工具类 TokenUtil 测试")
class TokenUtilTest {

    /** 改动前生效的历史密钥；一旦有人改动默认值，测试会立刻失败 */
    private static final String HISTORICAL_SECRET = "vsofo-5grcs-secret";

    @Test
    @DisplayName("🔒 用历史密钥签发的旧 token 仍能解析（改默认值会导致全体用户掉线）")
    void tokenSignedWithHistoricalSecret_stillParses() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("token_userid", "42");
        String legacy = "5grcs " + Jwts.builder()
                .setClaims(claims)
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(SignatureAlgorithm.HS512, HISTORICAL_SECRET)
                .compact();

        Map<String, String> info = TokenUtil.getMapInfoFromToken(legacy);

        assertEquals("42", info.get("token_userid"),
                "历史 token 解析失败 —— 说明默认密钥被改动，线上用户会全部掉线");
    }

    @Test
    @DisplayName("默认密钥即可正常签发与解析（含管理员角色声明）")
    void generateAndParse_roundTrip() {
        String userToken = TokenUtil.generateMerchantToken("42");
        assertTrue(userToken.startsWith("5grcs "), "token 必须以 '5grcs ' 开头，拦截器依赖此前缀");
        assertEquals("42", TokenUtil.getMapInfoFromToken(userToken).get("token_userid"));

        String adminToken = TokenUtil.generateAdminToken("1", "admin");
        Map<String, String> adminInfo = TokenUtil.getMapInfoFromToken(adminToken);
        assertEquals("1", adminInfo.get("token_userid"));
        assertEquals("admin", adminInfo.get("token_role"));
        assertEquals("admin", adminInfo.get("token_username"));
    }

    @Test
    @DisplayName("setSecret 可覆盖密钥（供环境变量 JWT_SECRET 注入），测后恢复默认")
    void setSecret_overridesSecret() {
        try {
            TokenUtil.setSecret("another-secret-for-test");
            String token = TokenUtil.generateMerchantToken("7");
            assertEquals("7", TokenUtil.getMapInfoFromToken(token).get("token_userid"),
                    "换密钥后应用新密钥签发并解析成功");
        } finally {
            TokenUtil.setSecret(HISTORICAL_SECRET);
        }
    }

    @Test
    @DisplayName("setSecret 传 null/空白 时忽略，不破坏已有密钥")
    void setSecret_ignoresBlank() {
        TokenUtil.setSecret(null);
        TokenUtil.setSecret("   ");

        String token = TokenUtil.generateMerchantToken("9");
        assertEquals("9", TokenUtil.getMapInfoFromToken(token).get("token_userid"),
                "空值不应覆盖密钥，否则签发与校验会不一致");
    }

    @Test
    @DisplayName("缺少 '5grcs ' 前缀 / 伪造 token 一律判定失效")
    void invalidTokens_areRejected() {
        assertTrue(TokenUtil.isTokenExpired("no-prefix-token"), "无前缀应判失效");
        assertTrue(TokenUtil.isTokenExpired("5grcs not-a-jwt"), "格式错误应判失效");
        assertEquals(0, TokenUtil.getMapInfoFromToken("no-prefix").size(), "无前缀应返回空信息");
    }
}
