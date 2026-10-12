package com.wwfinance.common.config;

import com.wwfinance.common.utils.TokenUtil;
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
 * <p><b>2026-10-08 抽取到 finance-common</b>：api 与 finance-oss 原先各有一份同构实现。
 * 两边校验的是<b>同一张</b> token（api 签发、存于同一 Redis），密钥必须一致，
 * 合并为一份后从根上杜绝「一处改了另一处没改导致全量 401」。</p>
 *
 * <p><b>默认值刻意与历史硬编码保持一致</b>：换值会导致所有已签发 token
 * 验签失败、全体在线用户掉线。生产环境请通过环境变量 {@code JWT_SECRET} 覆盖。</p>
 *
 * <p>装配方式：finance-oss 的 {@code @ComponentScan("com.wwfinance")} 会自动扫到本类；
 * api 不扫 {@code com.wwfinance}，故由 api 的 {@code MyWebConfig} 用 {@code @Import} 显式引入。</p>
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
