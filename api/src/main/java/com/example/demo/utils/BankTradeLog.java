package com.example.demo.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 银行往来流水日志（对账专用）。
 *
 * <p>背景：平台不持有资金，所有账户与划转都在存管银行侧。平台要证明"自己发起过什么、
 * 银行回过什么"，只能靠一条**与银行流水可逐笔对齐的日志**。因此本类不与业务日志混写，
 * 统一打到名为 {@code BANK_TRADE} 的独立 logger，由 logback 单独落到
 * {@code logs/bank-trade/bank-trade.log}（保留 90 天，便于跨月回溯对账）。</p>
 *
 * <p>三类事件，格式固定为「时间 | 事件 | 业务类型 | 字段」的单行文本，便于 grep / diff / 导入表格：</p>
 * <ul>
 *   <li>{@code REQ} —— 平台生成并发往银行的表单（出站请求）。落点在
 *       {@link com.example.demo.Bank.FormHelper#buildForm}，一处覆盖全部 6 个出站口。</li>
 *   <li>{@code CALLBACK} —— 银行异步回调（入站）。落点在
 *       {@code BankNotifyController}，一处覆盖全部 6 个回调口。</li>
 *   <li>{@code RECON} —— 每日对账任务的心跳与结论。落点在
 *       {@code ReconciliationServiceImpl#reconcileWithBank}。</li>
 * </ul>
 *
 * <p>为什么参数要按 key 排序：同一笔交易在"发起"与"回调"两侧的参数字典序不一致，
 * 排序后单行输出可以让两边肉眼或脚本直接对齐，且重复回调的日志完全可比对。</p>
 *
 * <p>注意：本类只做记录，不参与任何业务判断，方法永不抛异常，绝不影响主流程。</p>
 */
public final class BankTradeLog {

    /** 独立 logger 名，与业务 logger（com.example.demo）分离，由 logback 单独配置落盘。 */
    private static final Logger TRADE = LoggerFactory.getLogger("BANK_TRADE");

    /**
     * 出站表单地址 → 业务类型。
     * 地址取自 {@code HfbConst} 的 6 个出站 URL，只匹配路径片段，避免受 base-url 配置影响。
     */
    private static final Map<String, String> OUTBOUND_TYPES = new LinkedHashMap<>();

    static {
        OUTBOUND_TYPES.put("/userBind/BindAgreeUserV2", "BIND");
        OUTBOUND_TYPES.put("/userAccount/AgreeBankCharge", "RECHARGE");
        OUTBOUND_TYPES.put("/userAccount/CashBankManager", "WITHDRAW");
        OUTBOUND_TYPES.put("/userInvest/AgreeUserVoteProject", "INVEST");
        OUTBOUND_TYPES.put("/userInvest/AgreeAccountLendProject", "MAKE_LOAN");
        OUTBOUND_TYPES.put("/lendReturn/AgreeUserRepayment", "REPAY");
    }

    private BankTradeLog() {
    }

    /**
     * 记录一笔发往银行的表单请求。
     *
     * @param url    银行侧表单提交地址
     * @param params 表单全部字段
     */
    public static void outbound(String url, Map<String, Object> params) {
        TRADE.info("REQ|{}|{}|{}", bizTypeOf(url), url, kv(params));
    }

    /**
     * 记录一次银行异步回调的受理结果。
     *
     * @param bizType 业务类型（BIND/RECHARGE/WITHDRAW/INVEST/MAKE_LOAN/REPAY）
     * @param params  回调原始参数
     * @param ok      是否受理成功（false 表示银行会重试）
     * @param note    备注，null 记作 "-"
     */
    public static void callback(String bizType, Map<String, ?> params, boolean ok, String note) {
        TRADE.info("CALLBACK|{}|{}|{}|{}", bizType, ok ? "SUCCESS" : "FAIL",
                note == null ? "-" : note, kv(params));
    }

    /**
     * 记录回调处理过程中抛出的异常。**由调用方在 catch 中调用后原样抛出**，不吞异常。
     */
    public static void callbackError(String bizType, Map<String, ?> params, Exception e) {
        TRADE.error("CALLBACK|{}|ERROR|{}: {}|{}", bizType,
                e.getClass().getSimpleName(), e.getMessage(), kv(params));
    }

    /**
     * 记录一次对账任务的运行结论（无论有无差异都要落一条，用于证明任务确实跑过）。
     *
     * @param date      对账日期
     * @param bankRows  银行返回流水条数
     * @param localRows 本地流水条数，未知传 -1
     * @param summary   结论文案
     */
    public static void recon(String date, int bankRows, int localRows, String summary) {
        TRADE.info("RECON|{}|bank={}|local={}|{}", date, bankRows, localRows, summary);
    }

    /** 由出站地址推断业务类型，未识别返回 UNKNOWN。 */
    public static String bizTypeOf(String url) {
        if (url == null) {
            return "UNKNOWN";
        }
        for (Map.Entry<String, String> entry : OUTBOUND_TYPES.entrySet()) {
            if (url.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "UNKNOWN";
    }

    /** 参数按 key 字典序输出为单行，便于与银行流水逐笔对齐。 */
    private static String kv(Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        Map<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            sorted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.append('}').toString();
    }
}
