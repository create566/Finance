package com.example.demo.Bank;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * HfbConst 地址外置化的行为用例
 *
 * <p>钉住三件事：
 * <ol>
 *   <li>三个基地址注入后，21 个派生地址必须全部跟着变（避免有人改了配置、地址却还是 localhost）。</li>
 *   <li>结尾多余的斜杠要被吃掉，不能拼出 {@code https://x.com//path} 这种双斜杠。</li>
 *   <li>6 个「前端同步回跳」地址必须一律指向**免登录结果页** {@code /bank/return} ——
 *       指回 {@code /home} 会让银行回跳被路由守卫丢到登录页（"付完款反而退登"）。</li>
 * </ol>
 *
 * <p>⚠ HfbConst 的地址是静态字段，用例之间会互相污染，
 * 因此 {@code @AfterEach} 必须还原成本地默认值，否则会连累其他单测。</p>
 */
class HfbConstTest {

    private static final String DEF_BANK = "http://localhost:9090";
    private static final String DEF_NOTIFY = "http://localhost:8990";
    private static final String DEF_FRONT = "http://localhost:5174";

    @BeforeEach
    void setUp() {
        resetToDefault();
    }

    @AfterEach
    void tearDown() {
        // 静态字段必须还原，否则污染同 JVM 内后续用例
        resetToDefault();
    }

    private void resetToDefault() {
        new HfbConst().setBankBaseUrl(DEF_BANK);
        new HfbConst().setNotifyBaseUrl(DEF_NOTIFY);
        new HfbConst().setFrontBaseUrl(DEF_FRONT);
    }

    @Test
    @DisplayName("不配置时回落到本地模拟银行默认值")
    void defaultKeepsLocalhost() {
        assertEquals("http://localhost:9090/userAccount/AgreeBankCharge", HfbConst.RECHARGE_URL);
        assertEquals("http://localhost:8990/api/core/userAccount/notify", HfbConst.RECHARGE_NOTIFY_URL);
        assertEquals("http://localhost:5174/bank/return", HfbConst.RECHARGE_RETURN_URL);
    }

    @Test
    @DisplayName("注入生产域名后全部地址跟着变")
    void urlsFollowConfiguredBase() {
        HfbConst c = new HfbConst();
        c.setBankBaseUrl("https://bank.example.com");
        c.setNotifyBaseUrl("https://api.example.com");
        c.setFrontBaseUrl("https://www.example.com");

        // 银行网关
        assertEquals("https://bank.example.com/userBind/BindAgreeUserV2", HfbConst.USERBIND_URL);
        assertEquals("https://bank.example.com/userAccount/AgreeBankCharge", HfbConst.RECHARGE_URL);
        assertEquals("https://bank.example.com/userAccount/CashBankManager", HfbConst.WITHDRAW_URL);
        assertEquals("https://bank.example.com/userInvest/AgreeUserVoteProject", HfbConst.INVEST_URL);
        assertEquals("https://bank.example.com/userInvest/AgreeAccountLendProject", HfbConst.MAKE_LOAD_URL);
        assertEquals("https://bank.example.com/lendReturn/AgreeUserRepayment", HfbConst.BORROW_RETURN_URL);

        // 异步回调（提交给银行，必须是银行可达地址）
        assertEquals("https://api.example.com/api/core/userBind/notify", HfbConst.USERBIND_NOTIFY_URL);
        assertEquals("https://api.example.com/api/core/userAccount/notify", HfbConst.RECHARGE_NOTIFY_URL);
        assertEquals("https://api.example.com/api/core/userAccount/notifyWithdraw", HfbConst.WITHDRAW_NOTIFY_URL);
        assertEquals("https://api.example.com/api/core/lendItem/notify", HfbConst.INVEST_NOTIFY_URL);
        assertEquals("https://api.example.com/api/core/lend/notifyMakeLoan", HfbConst.MAKE_LOAD_NOTIFY_URL);
        assertEquals("https://api.example.com/api/core/lendReturn/notifyUrl", HfbConst.BORROW_RETURN_NOTIFY_URL);

        // 前端同步回调：6 个入口一律落在免登录结果页 /bank/return
        // （指回 /home 会在 host 不一致时被路由守卫丢到登录页 ⇒ "付完款反而退登"）
        assertEquals("https://www.example.com/bank/return", HfbConst.USERBIND_RETURN_URL);
        assertEquals("https://www.example.com/bank/return", HfbConst.RECHARGE_RETURN_URL);
        assertEquals("https://www.example.com/bank/return", HfbConst.WITHDRAW_RETURN_URL);
        assertEquals("https://www.example.com/bank/return", HfbConst.INVEST_RETURN_URL);
        assertEquals("https://www.example.com/bank/return", HfbConst.MAKE_LOAD_RETURN_URL);
        assertEquals("https://www.example.com/bank/return", HfbConst.BORROW_RETURN_RETURN_URL);
    }

    @Test
    @DisplayName("基地址结尾多余斜杠要被吃掉，不能拼出双斜杠")
    void trailingSlashIsTrimmed() {
        HfbConst c = new HfbConst();
        c.setBankBaseUrl("https://bank.example.com///");
        c.setNotifyBaseUrl("https://api.example.com/");
        c.setFrontBaseUrl("https://www.example.com//");

        assertEquals("https://bank.example.com/userAccount/AgreeBankCharge", HfbConst.RECHARGE_URL);
        assertEquals("https://api.example.com/api/core/userAccount/notify", HfbConst.RECHARGE_NOTIFY_URL);
        assertEquals("https://www.example.com/bank/return", HfbConst.RECHARGE_RETURN_URL);
    }

    @Test
    @DisplayName("传入空值回落到默认值而不是拼出空地址")
    void blankFallsBackToDefault() {
        HfbConst c = new HfbConst();
        c.setBankBaseUrl("   ");
        assertEquals("http://localhost:9090/userAccount/AgreeBankCharge", HfbConst.RECHARGE_URL);
    }

    /**
     * 回跳地址必须「用户从哪个 host 来就回哪个 host 去」，否则 cookie 带不过去 ⇒ 掉登录页。
     * 这正是「出借成功 → 点返回平台 → 登录页」的根因，务必钉住。
     */
    @Test
    @DisplayName("回跳地址按请求 Origin 动态解析；Referer 截断成 origin")
    void returnUrlFollowsRequestOrigin() {
        // Origin 本来就只有 scheme://host[:port]
        assertEquals("http://127.0.0.1:5174/bank/return",
                HfbConst.resolveReturnUrl("http://127.0.0.1:5174"));
        assertEquals("http://120.26.132.5/bank/return",
                HfbConst.resolveReturnUrl("http://120.26.132.5"));
        assertEquals("https://demo.example.com/bank/return",
                HfbConst.resolveReturnUrl("https://demo.example.com"));

        // Referer 带 path / query，必须截断成 origin，不能把 path 拼进去
        assertEquals("http://120.26.132.5:81/bank/return",
                HfbConst.resolveReturnUrl("http://120.26.132.5:81/invest/detail/101?amount=1000"));
    }

    @Test
    @DisplayName("Origin 非法 / 缺失时回落到配置的前端基地址")
    void returnUrlFallsBackWhenOriginUnusable() {
        String fallback = "http://localhost:5174/bank/return";
        assertEquals(fallback, HfbConst.resolveReturnUrl(null));
        assertEquals(fallback, HfbConst.resolveReturnUrl("   "));
        assertEquals(fallback, HfbConst.resolveReturnUrl("null"));      // 浏览器会发字面量 "null"
        assertEquals(fallback, HfbConst.resolveReturnUrl("file:///d:/x.html")); // 非 http(s) 一律不受理
        assertEquals(fallback, HfbConst.resolveReturnUrl("not a url"));
        assertEquals(fallback, HfbConst.resolveReturnUrl("http://"));   // 缺 authority
    }

    @Test
    @DisplayName("无请求上下文时 returnUrl() 与配置兜底字段一致（定时任务 / 纯单测）")
    void returnUrlUsesConfiguredFrontWithoutRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        assertNull(RequestContextHolder.getRequestAttributes());
        assertEquals(HfbConst.RECHARGE_RETURN_URL, HfbConst.returnUrl());
        assertEquals("http://localhost:5174/bank/return", HfbConst.returnUrl());
    }
}
