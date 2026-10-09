package com.example.demo.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 银行账户同步服务
 *
 * 核心职责：
 *   平台（ww_finance）只做记账，银行（ww_bank）管钱。
 *   本服务负责：
 *   1. 从银行获取用户真实余额（平台不再自己存余额）
 *   2. 将银行真实余额同步到平台账本（对账/初始化）
 *   3. 确保平台账本与银行实际资金一致
 */
public interface BankAccountSyncService {

    /**
     * 从银行获取用户真实可用余额
     *
     * @param userId 用户ID
     * @return 银行实际余额
     */
    BigDecimal getBankBalance(Long userId);

    /**
     * 【P0-1 统一可用额口径】用户当前真正能动的钱 = 银行余额 − 全部扣款类在途金额。
     *
     * <p><b>为什么必须有这一层：</b>银行侧扣款<b>不校验余额</b> —— javap 确认
     * {@code lockAccount}（投标冻结）、{@code withdraw}（提现）、{@code transferAccounts}（划转）
     * 三者都是无条件加减、永远返回成功。平台只在"生成表单前"读一次余额，
     * 挡不住并发提交 / 重复回调 / 回调重放，结果就是把账户扣成负数（库里已存在 3 个负余额账户）。
     *
     * <p>此前提现侧自己算了「余额 − 在途提现」，而投标与还款<b>直接用裸余额</b>，
     * 三个入口三套口径 —— 这里把它们收敛成一个函数，任何动钱入口都只能用它。
     *
     * <p>扣款类在途 = 提现(2) / 投标(3) / 还款(6) 中 {@code status=0（处理中）} 的流水之和。
     *
     * @param userId 用户ID
     * @return 可用余额（不会为负，最低 0）
     */
    BigDecimal getAvailableBalance(Long userId);

    /**
     * 该用户当前「扣款类在途」合计（提现 + 投标 + 还款，status=0）。
     * 用于给余额不足的报错补一段可解释的原因文案。
     */
    BigDecimal getPendingDebitAmount(Long userId);

    /**
     * 从银行获取用户真实冻结金额
     *
     * @param userId 用户ID
     * @return 银行实际冻结金额
     */
    BigDecimal getBankFreezeAmount(Long userId);

    /**
     * 同步银行余额到平台账本
     * 将银行真实余额写入平台 user_account（作为镜像账本）
     *
     * @param userId 用户ID
     */
    void syncBalance(Long userId);

    /**
     * 全量同步所有用户的银行余额到平台账本
     * 用于初始化或对账修复
     */
    void syncAllBalances();

    /**
     * 调用银行API查询用户余额（请求银行接口）
     *
     * @param userId 用户ID
     * @return 包含 amount 和 freezeAmount 的 Map
     */
    Map<String, Object> queryBankAccount(Long userId);

    /**
     * 调用银行API查询当日流水（用于对账）
     *
     * @param date 对账日期
     * @return 银行流水列表
     */
    List<Map<String, Object>> queryBankFlow(String date);

    /**
     * 【P0-1 对账兜底】扫描银行侧所有账实异常的账户。
     *
     * <p>银行扣款不校验余额（{@code lockAccount} / {@code withdraw} / {@code transferAccounts}
     * 三者都是无条件加减），所以负余额只能靠<b>事后扫描</b>发现 —— 平台前置闸门挡不住
     * 并发、重放与直接改库。
     *
     * <p>判定口径：{@code amount < 0}（余额为负）或 {@code freeze_amount < 0}（冻结为负）。
     *
     * @return 每条含 {@code userCode / amount / freezeAmount}；查不到或异常时返回空列表
     */
    List<Map<String, Object>> findAbnormalAccounts();
}
