package com.example.demo.Bank;

/**
 * 常量定义
 */
public class HfbConst {

    //给商户分配的唯一标识（从配置读取）
    public static final String AGENT_ID = System.getenv().getOrDefault("BANK_AGENT_ID", "999888");
    //签名key（从配置读取）
    public static final String SIGN_KEY = System.getenv().getOrDefault("BANK_SIGN_KEY", "9876543210");

    /**
     * 用户绑定
     */
    //用户绑定旺旺银行平台url地址
    public static final String USERBIND_URL = "http://localhost:9090/userBind/BindAgreeUserV2";
    //用户绑定异步回调
    public static final String USERBIND_NOTIFY_URL = "http://localhost:8990/api/core/userBind/notify";
    //用户绑定同步回调
    public static final String USERBIND_RETURN_URL = "http://localhost:5174/home";

    /**
     * 充值
     */
    //充值旺旺银行平台url地址
    public static final String RECHARGE_URL = "http://localhost:9090/userAccount/AgreeBankCharge";
    //充值异步回调
    public static final String RECHARGE_NOTIFY_URL = "http://localhost:8990/api/core/userAccount/notify";
    //充值同步回调
    public static final String RECHARGE_RETURN_URL = "http://localhost:5174/home";

    /**
     * 投标
     */
    //充值旺旺银行平台url地址
    public static final String INVEST_URL = "http://localhost:9090/userInvest/AgreeUserVoteProject";
    //充值异步回调
    public static final String INVEST_NOTIFY_URL = "http://localhost:8990/api/core/lendItem/notify";
    //充值同步回调
    public static final String INVEST_RETURN_URL = "http://localhost:5174/home";

    /**
     * 放款
     */
    public static final String MAKE_LOAD_URL = "http://localhost:9090/userInvest/AgreeAccountLendProject";
    //放款异步回调
    public static final String MAKE_LOAD_NOTIFY_URL = "http://localhost:8990/api/core/lend/notifyMakeLoan";
    //放款同步回调
    public static final String MAKE_LOAD_RETURN_URL = "http://localhost:5174/home";

    /**
     * 提现
     */
    //充值旺旺银行平台url地址
    public static final String WITHDRAW_URL = "http://localhost:9090/userAccount/CashBankManager";
    //充值异步回调
    public static final String WITHDRAW_NOTIFY_URL = "http://localhost:8990/api/core/userAccount/notifyWithdraw";
    //充值同步回调
    public static final String WITHDRAW_RETURN_URL = "http://localhost:5174/home";

    /**
     * 还款扣款
     */
    //充值旺旺银行平台url地址
    public static final String BORROW_RETURN_URL = "http://localhost:9090/lendReturn/AgreeUserRepayment";
    //充值异步回调
    public static final String BORROW_RETURN_NOTIFY_URL = "http://localhost:8990/api/core/lendReturn/notifyUrl";
    //充值同步回调
    public static final String BORROW_RETURN_RETURN_URL = "http://localhost:5174/home";

}