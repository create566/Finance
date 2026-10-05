package com.example.demo.config;

import com.example.demo.utils.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 把配置里的 JWT 密钥注入静态工具类 {@link TokenUtil}。
 *
 * <p>背景：TokenUtil 是纯静态工具类（被拦截器与登录服务以静态方式调用，
 * 改成 Spring Bean 会波及全部调用点），其 secret 原为硬编码，
 * 无法直接 {@code @Value} 注入，故由本类在启动时写入。</p>
 *
 * <p><b>默认值刻意与历史硬编码保持一致</b>：换值会导致所有已签发 token
 * 验签失败、全体在线用户掉线。生产环境请通过环境变量 {@code JWT_SECRET} 覆盖。</p>
 */
@Slf4j
@Configuration
public class JwtSecretConfig {

    public JwtSecretConfig(@Value("${security.jwt.secret:vsofo-5grcs-secret}") String secret) {
        TokenUtil.setSecret(secret);
        if ("vsofo-5grcs-secret".equals(secret)) {
            log.warn("JWT 密钥当前为默认值（本地开发可接受）；生产环境请用环境变量 JWT_SECRET 覆盖");
        } else {
            log.info("JWT 密钥已从外部配置加载（非默认值）");
        }
    }
}
