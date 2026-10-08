package com.example.demo.Bank;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
 *   <li>{@code bank.front-base-url}  —— 银行处理完成后跳回的前端地址（默认 5174）</li>
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
     * 由三个基地址重算全部 21 个地址
     */
    private static void refresh() {
        USERBIND_URL = bankBaseUrl + "/userBind/BindAgreeUserV2";
        USERBIND_NOTIFY_URL = notifyBaseUrl + "/api/core/userBind/notify";
        USERBIND_RETURN_URL = frontBaseUrl + "/home";

        RECHARGE_URL = bankBaseUrl + "/userAccount/AgreeBankCharge";
        RECHARGE_NOTIFY_URL = notifyBaseUrl + "/api/core/userAccount/notify";
        RECHARGE_RETURN_URL = frontBaseUrl + "/home";

        INVEST_URL = bankBaseUrl + "/userInvest/AgreeUserVoteProject";
        INVEST_NOTIFY_URL = notifyBaseUrl + "/api/core/lendItem/notify";
        INVEST_RETURN_URL = frontBaseUrl + "/home";

        MAKE_LOAD_URL = bankBaseUrl + "/userInvest/AgreeAccountLendProject";
        MAKE_LOAD_NOTIFY_URL = notifyBaseUrl + "/api/core/lend/notifyMakeLoan";
        MAKE_LOAD_RETURN_URL = frontBaseUrl + "/home";

        WITHDRAW_URL = bankBaseUrl + "/userAccount/CashBankManager";
        WITHDRAW_NOTIFY_URL = notifyBaseUrl + "/api/core/userAccount/notifyWithdraw";
        WITHDRAW_RETURN_URL = frontBaseUrl + "/home";

        BORROW_RETURN_URL = bankBaseUrl + "/lendReturn/AgreeUserRepayment";
        BORROW_RETURN_NOTIFY_URL = notifyBaseUrl + "/api/core/lendReturn/notifyUrl";
        BORROW_RETURN_RETURN_URL = frontBaseUrl + "/home";
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
