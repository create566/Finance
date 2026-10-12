package com.example.demo.service.impl;

import com.example.demo.Bank.FormHelper;
import com.example.demo.Bank.HfbConst;
import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.UserBind;
import com.example.demo.entity.dto.AccountInfoDto;
import com.example.demo.enums.BindStatusEnum;
import com.example.demo.enums.TransStatusEnum;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.exception.BusinessException;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.example.demo.mapper.TransFlowMapper;
import com.example.demo.mapper.UserAccountMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.service.BankAccountSyncService;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.example.demo.utils.BalanceGuard;
import com.example.demo.utils.LendNoUtils;
import com.wwfinance.common.utils.BankSignUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Log4j2
@Service
public class UserAccountServiceImpl implements UserAccountService {

    @Autowired
    private UserService userService;

    @Autowired
    private UserBindService userBindService;

    @Autowired
    private BankAccountSyncService bankAccountSyncService;

    @Autowired
    private UserAccountMapper userAccountMapper;

    /**
     * 直接注入 Mapper 而不是 TransFlowService：
     * TransFlowServiceImpl 反过来依赖本类，走 Service 会形成循环依赖。
     */
    @Autowired
    private TransFlowMapper transFlowMapper;

    @Override
    public AccountInfoDto getAccountInfo(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "用户不存在");
        }
        BigDecimal balance = bankAccountSyncService.getBankBalance(userId);
        BigDecimal freezeAmount = bankAccountSyncService.getBankFreezeAmount(userId);
        UserBind userBind = userBindService.getByUserId(userId);

        AccountInfoDto dto = new AccountInfoDto();
        dto.setUserId(user.getId());
        dto.setMobile(user.getMobile());
        dto.setName(user.getName());
        dto.setIdCard(user.getIdCard());
        dto.setUserType(user.getUserType());
        dto.setBorrowAuthStatus(user.getBorrowAuthStatus());
        dto.setAmount(balance);
        dto.setFreezeAmount(freezeAmount);
        dto.setBindStatus(userBind != null ? userBind.getStatus() : 0);
        dto.setBindCode(userBind != null ? userBind.getBindCode() : null);
        return dto;
    }

    /**
     * 充值：先生成业务单号并落一条「处理中」流水，再把付款表单交给用户去银行完成。
     *
     * 为什么要先落流水：银行回调没到之前（甚至永远不会到）用户是看不到任何记录的，
     * 【我的资金 → 资金流水】会显得"点了充值没反应"。市场通行做法是发起即落一笔
     * 处理中流水，回调再置成功/失败 —— 用户随时能看到"我这笔在途"。
     * agentBillNo 会被银行在回调里原样回带，平台据此定位这条流水（见
     * {@code TransFlowServiceImpl.handleRechargeNotify}）。
     */
    @Override
    public String buildRechargeForm(Long userId, BigDecimal amount) {
        UserBind userBind = getBoundUser(userId);
        BalanceGuard.requirePositive(amount, "充值");

        String transNo = LendNoUtils.getChargeNo();
        savePendingFlow(userId, TransTypeEnum.RECHARGE, amount, "用户充值（待银行处理）", transNo);

        Map<String, Object> paramMap = buildBankParams(userBind, amount, "chargeAmt", transNo,
                HfbConst.returnUrl(), HfbConst.RECHARGE_NOTIFY_URL);
        return FormHelper.buildForm(HfbConst.RECHARGE_URL, paramMap);
    }

    /**
     * 提现：相对于充值多一道闸门 —— 提现要动用户可用余额，余额不够就不允许提交。
     *
     * <p><b>可用余额必须扣掉「在途」提现</b>。银行余额只有在银行回调（
     * {@code handleWithdrawNotify}）之后才会真正减少，而用户在银行页面完成付款之前
     * 那条「处理中」流水一直是 PROCESSING。若只拿银行余额做闸门：
     * <pre>
     *   余额 1000 → 提 1000（受理，银行余额仍 1000）→ 再提 1000（又受理）
     * </pre>
     * 同一笔钱被重复提交两次，平台侧却"看起来完全合规"。这是典型的
     * check-then-act 非原子缺口，且不需要并发就能触发（间隔几秒连点两次即可）。
     * 因此可用余额 = 银行余额 − Σ(处理中提现)。提现失败回调会把流水置为 FAILED，
     * 占用随之释放；成功回调置为 SUCCESS 时银行余额也已同步减少，不会重复扣减。
     */
    @Override
    public String buildWithdrawForm(Long userId, BigDecimal amount) {
        UserBind userBind = getBoundUser(userId);
        BalanceGuard.requirePositive(amount, "提现");

        BigDecimal balance = bankAccountSyncService.getBankBalance(userId);
        // 【P0-1】可用额统一走 BankAccountSyncService.getAvailableBalance：
        // 银行余额 − 全部扣款类在途（提现 + 投标 + 还款）。
        // 原来这里只扣"在途提现"，用户在途的投标/还款额度会被重复算成可用 → 并发即透支。
        BigDecimal available = bankAccountSyncService.getAvailableBalance(userId);
        BigDecimal pending = bankAccountSyncService.getPendingDebitAmount(userId);

        // 余额本身够、但被在途单子占满了 —— 直接套用 BalanceGuard 的话用户会看到
        // "当前可用 ¥0" 而自己的余额明明是 1000，务必给出可解释的原因。
        if (pending.compareTo(BigDecimal.ZERO) > 0
                && available.compareTo(amount) < 0
                && balance.compareTo(amount) >= 0) {
            throw new BusinessException(400, String.format(
                    "提现失败：您有 ¥%s 资金在途（提现/投标/还款待银行处理），扣除后可用 ¥%s，本次需 ¥%s。请等待在途资金处理完成后再试。",
                    pending.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                    available.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                    amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()));
        }
        BalanceGuard.require("提现", available, amount);

        String transNo = LendNoUtils.getWithdrawNo();
        savePendingFlow(userId, TransTypeEnum.WITHDRAW, amount, "用户提现（待银行处理）", transNo);

        Map<String, Object> paramMap = buildBankParams(userBind, amount, "fetchAmt", transNo,
                HfbConst.returnUrl(), HfbConst.WITHDRAW_NOTIFY_URL);
        return FormHelper.buildForm(HfbConst.WITHDRAW_URL, paramMap);
    }

    /**
     * 落一条「处理中」流水。
     * 银行回调到达时按 trans_no 更新为成功/失败，不会重复插入。
     */
    private void savePendingFlow(Long userId, TransTypeEnum type, BigDecimal amount,
                                 String memo, String transNo) {
        User user = userService.getById(userId);
        TransFlow flow = new TransFlow()
                .setUserId(userId)
                .setUserName(user != null ? user.getName() : null)
                .setTransNo(transNo)
                .setTransType(type.getCode())
                .setTransTypeName(type.getDesc())
                .setTransAmount(amount)
                .setStatus(TransStatusEnum.PROCESSING.getCode())
                .setMemo(memo)
                .setCreateTime(java.time.LocalDateTime.now())
                .setUpdateTime(java.time.LocalDateTime.now());
        transFlowMapper.insert(flow);
        log.info("已落处理中流水：userId={}, type={}, amount={}, transNo={}",
                userId, type.getDesc(), amount, transNo);
    }

    private UserBind getBoundUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "用户不存在");
        }
        UserBind userBind = userBindService.getByUserId(userId);
        if (userBind == null || !BindStatusEnum.BOUND.getCode().equals(userBind.getStatus())) {
            throw new BusinessException(400, "请先完成绑卡");
        }
        userBind.setMobile(user.getMobile());
        return userBind;
    }

    private Map<String, Object> buildBankParams(UserBind userBind, BigDecimal amount, String amountKey,
                                                 String transNo, String returnUrl, String notifyUrl) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", HfbConst.AGENT_ID);
        paramMap.put("agentUserId", userBind.getMobile());
        paramMap.put("bindCode", userBind.getBindCode());
        paramMap.put(amountKey, amount);
        // 业务单号：银行回调会原样回带，用于把「处理中」流水更新成成功/失败
        paramMap.put("agentBillNo", transNo);
        paramMap.put("returnUrl", returnUrl);
        paramMap.put("notifyUrl", notifyUrl);
        paramMap.put("timestamp", new Date().getTime());
        paramMap.put("sign", BankSignUtil.getSign(paramMap));
        return paramMap;
    }

    @Override
    public void updateBalance(Long userId, TransTypeEnum type, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "余额变动金额非法");
        }
        int rows = applyAtomicUpdate(userId, type, amount);
        if (rows == 0) {
            // 0 行：账户不存在（或扣款时余额不足）。先尝试建账户再重试一次
            ensureAccount(userId);
            rows = applyAtomicUpdate(userId, type, amount);
        }
        if (rows == 0) {
            throw new BusinessException(400, String.format(
                    "账户余额不足：本次需扣 ¥%s，扣款未执行。请先充值后重试。",
                    amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()));
        }
        log.info("账户余额原子更新成功: userId={}, type={}, amount={}",
                userId, type.getDesc(), amount);
    }

    /**
     * 按类型执行原子余额更新（SQL 层 amount = amount ± ?，避免先查后改的并发丢更新）
     *
     * @return 影响行数；-1 表示未知类型（不执行任何更新）
     */
    private int applyAtomicUpdate(Long userId, TransTypeEnum type, BigDecimal amount) {
        switch (type) {
            case RECHARGE:
            case RETURN:
            case LOAN:
                return userAccountMapper.addAmount(userId, amount);
            case WITHDRAW:
            case INVEST:
            case REPAY:
                return userAccountMapper.deductAmount(userId, amount);
            default:
                log.warn("未知的余额变动类型，忽略，type={}", type);
                return -1;
        }
    }

    private void ensureAccount(Long userId) {
        UserAccount existing = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUserId, userId));
        if (existing == null) {
            UserAccount account = new UserAccount();
            account.setUserId(userId);
            account.setAmount(BigDecimal.ZERO);
            account.setFreezeAmount(BigDecimal.ZERO);
            userAccountMapper.insert(account);
        }
    }
}