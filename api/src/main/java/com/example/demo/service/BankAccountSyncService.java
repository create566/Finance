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
}
