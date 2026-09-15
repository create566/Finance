package com.example.demo.service;

import com.example.demo.entity.dto.AccountInfoDto;
import java.math.BigDecimal;

/**
 * 用户账户服务（撮合方，不碰资金）
 *
 * 核心原则：平台只做信息中介和账本镜像，不持有、不操作用户资金。
 * 所有真实余额来自银行（ww_bank），平台仅从银行查询并展示。
 * 所有资金变动由银行处理，平台只记录交易流水（对账用）。
 */
public interface UserAccountService {

    /**
     * 从银行获取用户真实可用余额（平台不存储余额）
     *
     * @param userId 用户ID
     * @return 银行实际余额
     */
    BigDecimal getBalance(Long userId);

    /**
     * 从银行获取用户真实冻结金额
     *
     * @param userId 用户ID
     * @return 银行实际冻结金额
     */
    BigDecimal getFreezeAmount(Long userId);

    /**
     * 查询用户完整账户信息（全部来自银行）
     *
     * @param userId 用户ID
     * @return 账户信息
     */
    AccountInfoDto getAccountInfo(Long userId);

    /**
     * 发起充值（仅组装银行表单，不碰触余额）
     */
    String buildRechargeForm(Long userId, BigDecimal amount);

    /**
     * 发起提现（仅组装银行表单，不碰触余额）
     */
    String buildWithdrawForm(Long userId, BigDecimal amount);
}
