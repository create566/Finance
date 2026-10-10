package com.example.demo.Bank;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;

/**
 * 银行对接常量定义
 *
 * <p><b>2026-10-06 改造：地址外置化。</b>
 * 原先 21 处地址全部写死 {@code http://localhost:9090 / :8990 / :5174}，
 * 部署到任何非本机环境后，充值 / 提现 / 投标 / 放款 / 还款五条动钱链路会全部指向自己，
 * 且银行永远回调不到本机 —— 这是上线第一阻塞项。
 *
 * <p>改造后收敛为三个基地址，由 Spring 配置注入：
 * <ul>
 *   <li>{@code bank.base-url}    —— 银行网关地址（默认本地模拟银行 9090）</li>
 *   <li>{@code bank.notify-base-url} —— 我方接收异步回调的外网地址（默认 8990）</li>
 *   <li>{@code bank.front-base-url}  —— 银行处理完成后跳回的前端地址（默认 5174），
 *       <b>仅作兜底</b>。基地址优先按<b>当前请求的 Origin</b> 动态解析（见 {@link #returnUrl()}），
 *       回跳路径固定为免登录结果页 {@code /bank/return}（不再是 {@code /home}）。</li>
 * </ul>
 *
 * <p><b>为什么保留静态字段而不是改成实例注入：</b>
 * {@code HfbConst.X} 的写法散落在 6 个 Service 共 26 处，改成实例注入需要全量改调用点，
 * 收益与风险不成比例。这里用「静态字段 + &#64;Value setter」做最小改动迁移：
 * 字段在类加载时就用默认值初始化完毕，因此不启动 Spring 容器时（如纯 Mockito 单测）
 * 依然拿到 localhost 默认值，不会 NPE，行为与改造前完全一致。
 *
 * <p><b>部署必读：</b>三个基地址必须显式配置，尤其是 {@code bank.notify-base-url} ——
 * 它是提交给银行的回调地址，必须是银行能访问到的公网地址，填 localhost 等于回调永远收不到。
 */
@Slf4j
@Component
public class HfbConst {

    //给商户分配的唯一标识（从配置读取）
    public static final String AGENT_ID = System.getenv().getOrDefault("BANK_AGENT_ID", "999888");
    //签名key（从配置读取）
    public static final String SIGN_KEY = System.getenv().getOrDefault("BANK_SIGN_KEY", "9876543210");

    /** 基地址默认值：本地模拟银行 / 本地后端 / 本地用户端 */
    private static final String DEF_BANK = "http://localhost:9090";
    private static final String DEF_NOTIFY = "http://localhost:8990";
    private static final String DEF_FRONT = "http://localhost:5174";

    /**
     * 银行处理完成后回跳的**前端落地页路径**（用户端 router 里的免登录路由）。
     *
     * <p><b>2026-10-10 修复：为什么不再直接回跳 {@code /home}</b>
     * <p>{@code /home} 在用户端路由守卫的白名单之外，而银行回跳是**一次全新的整页加载**，
     * 登录态 {@code user_token} 又是**按 host 存**的 cookie。只要回跳 host 与用户实际访问的 host
     * 不一致 —— {@code 127.0.0.1} vs {@code localhost} 这种最常踩，或用户改用 IP / 域名而配置没跟着改 ——
     * cookie 就带不过来，路由守卫随即把用户丢到登录页。用户刚付完钱看到的却是登录页，
     * 这就是「出借成功 → 点返回平台 → 掉登录页」的根因。
     * <p>改成这个**免登录**的独立结果页后，回跳不再依赖登录态。用户端在该页检测
     * {@code window.opener}：是新窗口就倒计时自动关闭，让用户回到原标签页看最新余额 / 进度。
     *
     * <p><b>2026-10-10 二次修复：连 host 也不能写死。</b>
     * 只把路径从 {@code /home} 换成免登录页还不够 —— 基地址若固定取配置的
     * {@code bank.front-base-url}，一套部署的多条访问路径（{@code localhost:5174}、
     * {@code 127.0.0.1:5174}、公网 IP / 域名）里只有一个是对的，其余仍会跳到**另一个 origin**：
     * cookie 依旧带不过去（公网访问时甚至会打到用户自己电脑的 localhost）。
     * 因此基地址改为按**当前请求的 Origin** 动态推导（见 {@link #returnUrl()}），
     * 保证「用户从哪个 host 来，就回哪个 host 去」。
     */
    private static final String RETURN_PAGE_PATH = "/bank/return";

    private static String bankBaseUrl = DEF_BANK;
    private static String notifyBaseUrl = DEF_NOTIFY;
    private static String frontBaseUrl = DEF_FRONT;

    /**
     * 用户绑定
     */
    public static String USERBIND_URL;
    public static String USERBIND_NOTIFY_URL;
    public static String USERBIND_RETURN_URL;

    /**
     * 充值
     */
    public static String RECHARGE_URL;
    public static String RECHARGE_NOTIFY_URL;
    public static String RECHARGE_RETURN_URL;

    /**
     * 投标
     */
    public static String INVEST_URL;
    public static String INVEST_NOTIFY_URL;
    public static String INVEST_RETURN_URL;

    /**
     * 放款
     */
    public static String MAKE_LOAD_URL;
    public static String MAKE_LOAD_NOTIFY_URL;
    public static String MAKE_LOAD_RETURN_URL;

    /**
     * 提现
     */
    public static String WITHDRAW_URL;
    public static String WITHDRAW_NOTIFY_URL;
    public static String WITHDRAW_RETURN_URL;

    /**
     * 还款扣款
     */
    public static String BORROW_RETURN_URL;
    public static String BORROW_RETURN_NOTIFY_URL;
    public static String BORROW_RETURN_RETURN_URL;

    static {
        refresh();
    }

    @Value("${bank.base-url:http://localhost:9090}")
    public void setBankBaseUrl(String url) {
        bankBaseUrl = normalize(url, DEF_BANK);
        refresh();
    }

    @Value("${bank.notify-base-url:http://localhost:8990}")
    public void setNotifyBaseUrl(String url) {
        notifyBaseUrl = normalize(url, DEF_NOTIFY);
        refresh();
    }

    @Value("${bank.front-base-url:http://localhost:5174}")
    public void setFrontBaseUrl(String url) {
        frontBaseUrl = normalize(url, DEF_FRONT);
        refresh();
    }

    /**
     * 由三个基地址重算全部 21 个地址。
     *
     * <p>⚠️ 其中 6 个 {@code *_RETURN_URL} 只是<b>配置兜底值</b>（无请求上下文时用），
     * 真正出站时一律取 {@link #returnUrl()} —— 它按当前请求 Origin 解析，
     * 见 {@link #RETURN_PAGE_PATH} 的常量说明。
     */
    private static void refresh() {
        USERBIND_URL = bankBaseUrl + "/userBind/BindAgreeUserV2";
        USERBIND_NOTIFY_URL = notifyBaseUrl + "/api/core/userBind/notify";
        USERBIND_RETURN_URL = frontBaseUrl + RETURN_PAGE_PATH;

        RECHARGE_URL = bankBaseUrl + "/userAccount/AgreeBankCharge";
        RECHARGE_NOTIFY_URL = notifyBaseUrl + "/api/core/userAccount/notify";
        RECHARGE_RETURN_URL = frontBaseUrl + RETURN_PAGE_PATH;

        INVEST_URL = bankBaseUrl + "/userInvest/AgreeUserVoteProject";
        INVEST_NOTIFY_URL = notifyBaseUrl + "/api/core/lendItem/notify";
        INVEST_RETURN_URL = frontBaseUrl + RETURN_PAGE_PATH;

        MAKE_LOAD_URL = bankBaseUrl + "/userInvest/AgreeAccountLendProject";
        MAKE_LOAD_NOTIFY_URL = notifyBaseUrl + "/api/core/lend/notifyMakeLoan";
        MAKE_LOAD_RETURN_URL = frontBaseUrl + RETURN_PAGE_PATH;

        WITHDRAW_URL = bankBaseUrl + "/userAccount/CashBankManager";
        WITHDRAW_NOTIFY_URL = notifyBaseUrl + "/api/core/userAccount/notifyWithdraw";
        WITHDRAW_RETURN_URL = frontBaseUrl + RETURN_PAGE_PATH;

        BORROW_RETURN_URL = bankBaseUrl + "/lendReturn/AgreeUserRepayment";
        BORROW_RETURN_NOTIFY_URL = notifyBaseUrl + "/api/core/lendReturn/notifyUrl";
        BORROW_RETURN_RETURN_URL = frontBaseUrl + RETURN_PAGE_PATH;
    }

    /**
     * 银行处理完成后的回跳地址，**按当前请求的 Origin 动态解析**。
     *
     * <p>六个出站点（绑卡 / 充值 / 提现 / 投标 / 放款 / 还款）都应当用这个方法，
     * 而不是直接用 {@code *_RETURN_URL} 静态字段。理由见 {@link #RETURN_PAGE_PATH}。
     *
     * <p>安全口径：Origin 只截取 {@code scheme://host[:port]}（丢弃 path / query），
     * 且能在 Spring MVC 线程里拿到 Origin 的请求，都已经过网关 {@code CorsWebFilter}
     * 的白名单校验（非白名单 Origin 会被网关直接 403），因此不会被用来构造任意外站跳转。
     *
     * @return 形如 {@code http://<用户访问的host>:<port>/bank/return}
     */
    public static String returnUrl() {
        return resolveReturnUrl(currentRequestOrigin());
    }

    /**
     * {@link #returnUrl()} 的纯函数内核，便于单测（不依赖 Servlet 环境）。
     *
     * @param rawOrigin 原始 Origin / Referer 头，可为 null
     */
    static String resolveReturnUrl(String rawOrigin) {
        String origin = normalizeOrigin(rawOrigin);
        return (origin != null ? origin : frontBaseUrl) + RETURN_PAGE_PATH;
    }

    /**
     * 取当前请求的前端来源；非 Web 线程（定时任务 / 纯单测）返回 null。
     *
     * <p>先看 {@code Origin}（POST / PUT 等非简单请求浏览器必带），
     * 没有再退到 {@code Referer}（GET 场景下引用页的完整地址，截断后即 origin）。
     */
    private static String currentRequestOrigin() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof ServletRequestAttributes)) {
                return null;
            }
            HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
            if (request == null) {
                return null;
            }
            String origin = request.getHeader("Origin");
            return origin != null ? origin : request.getHeader("Referer");
        } catch (Exception e) {
            log.debug("无请求上下文，前端基地址回落配置值 frontBaseUrl={}", frontBaseUrl);
            return null;
        }
    }

    /**
     * 把 Origin / Referer 归一化成 {@code scheme://host[:port]}；非法值一律返回 null。
     *
     * <p>只接受 http / https；浏览器在某些场景会发字面量 {@code "null"}，一并视为无效。
     */
    private static String normalizeOrigin(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String authority = uri.getAuthority();
            if (scheme == null || authority == null) {
                return null;
            }
            scheme = scheme.toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            return scheme + "://" + authority;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 去掉结尾多余的斜杠，空值回落到默认值
     */
    private static String normalize(String url, String defaultValue) {
        if (url == null || url.trim().isEmpty()) {
            return defaultValue;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
