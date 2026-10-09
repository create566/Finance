package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.Bank.HfbConst;
import com.example.demo.Bank.RequestHelper;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAccount;
import com.example.demo.enums.TransStatusEnum;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.mapper.UserAccountMapper;
import com.example.demo.service.BankAccountSyncService;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserService;
import com.wwfinance.common.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class BankAccountSyncServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount>
        implements BankAccountSyncService {

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    @Qualifier("bankJdbcTemplate")
    private JdbcTemplate bankJdbcTemplate;

    @Autowired
    private com.example.demo.mapper.TransFlowMapper transFlowMapper;

    private static final String BANK_QUERY_URL = "http://localhost:9090/userAccount/queryBalance";
    private static final String BANK_FLOW_URL = "http://localhost:9090/userAccount/queryFlow";

    /**
     * 扣款类交易：会真实减少用户可用余额的三种动作。
     * 银行侧对它们一律不校验余额（javap 确认），所以平台必须自己算在途占用。
     */
    private static final List<Integer> DEBIT_TRANS_TYPES = Arrays.asList(
            TransTypeEnum.WITHDRAW.getCode(),
            TransTypeEnum.INVEST.getCode(),
            TransTypeEnum.REPAY.getCode());

    @Override
    public BigDecimal getAvailableBalance(Long userId) {
        BigDecimal balance = nvl(getBankBalance(userId));
        BigDecimal pending = getPendingDebitAmount(userId);
        BigDecimal available = balance.subtract(pending);
        // 余额本身已经为负（历史脏数据）时不要把负号再放大
        return available.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : available;
    }

    @Override
    public BigDecimal getPendingDebitAmount(Long userId) {
        try {
            BigDecimal sum = transFlowMapper.sumAmountByTypes(
                    userId, DEBIT_TRANS_TYPES, TransStatusEnum.PROCESSING.getCode());
            return nvl(sum);
        } catch (Exception e) {
            // 查不到在途就当 0，不能因为这层增强把正常业务拦死
            log.error("查询扣款类在途金额失败，userId={}，按 0 处理", userId, e);
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    public BigDecimal getBankBalance(Long userId) {
        try {
            String bindCode = getUserBindCode(userId);
            if (bindCode != null) {
                BigDecimal bankBalance = queryBankBalanceByBindCode(bindCode);
                if (bankBalance != null) {
                    log.info("从银行库查询余额成功，userId={}, bindCode={}, balance={}", userId, bindCode, bankBalance);
                    return bankBalance;
                }
            }
        } catch (Exception e) {
            log.warn("从银行库查询余额失败，降级查本地表，userId={}", userId, e);
        }
        try {
            UserAccount account = userAccountMapper.selectOne(
                    new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUserId, userId));
            if (account != null && account.getAmount() != null) {
                return account.getAmount();
            }
        } catch (Exception e) {
            log.error("查询平台余额失败，userId={}", userId, e);
        }
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getBankFreezeAmount(Long userId) {
        try {
            String bindCode = getUserBindCode(userId);
            if (bindCode != null) {
                BigDecimal freezeAmount = queryBankFreezeByBindCode(bindCode);
                if (freezeAmount != null) {
                    return freezeAmount;
                }
            }
        } catch (Exception e) {
            log.warn("从银行库查询冻结金额失败，降级查本地表，userId={}", userId, e);
        }
        try {
            UserAccount account = userAccountMapper.selectOne(
                    new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUserId, userId));
            if (account != null && account.getFreezeAmount() != null) {
                return account.getFreezeAmount();
            }
        } catch (Exception e) {
            log.error("查询平台冻结金额失败，userId={}", userId, e);
        }
        return BigDecimal.ZERO;
    }

    @Override
    public void syncBalance(Long userId) {
        try {
            Map<String, Object> bankAccount = queryBankAccount(userId);
            if (bankAccount == null) {
                log.warn("同步银行余额失败，用户 {} 银行无返回数据", userId);
                return;
            }
            BigDecimal bankAmount = new BigDecimal(bankAccount.get("amount").toString());
            BigDecimal bankFreeze = new BigDecimal(bankAccount.get("freezeAmount").toString());

            // 注意：不能 selectById(userId)——主键是账户自增id，不是用户id
            UserAccount account = userAccountMapper.selectOne(
                    new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUserId, userId));
            if (account == null) {
                account = new UserAccount();
                account.setUserId(userId);
                account.setCreateTime(LocalDateTime.now());
            }
            account.setAmount(bankAmount);
            account.setFreezeAmount(bankFreeze);
            account.setUpdateTime(LocalDateTime.now());
            saveOrUpdate(account);
            log.info("同步银行余额成功，userId={}, amount={}, freezeAmount={}",
                    userId, bankAmount, bankFreeze);
        } catch (Exception e) {
            log.error("同步用户 {} 余额失败", userId, e);
        }
    }

    @Override
    public void syncAllBalances() {
        log.info("开始全量同步银行余额到平台账本...");
        List<User> users = userService.list();
        int successCount = 0;
        int failCount = 0;
        for (User user : users) {
            try {
                syncBalance(user.getId());
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("同步用户 {} 余额失败", user.getId(), e);
            }
        }
        log.info("全量同步完成，总数：{}，成功：{}，失败：{}",
                users.size(), successCount, failCount);
    }

    @Override
    public Map<String, Object> queryBankAccount(Long userId) {
        try {
            User user = userService.getById(userId);
            if (user == null) {
                log.warn("查询银行余额失败：用户不存在，userId={}", userId);
                return null;
            }
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("agentId", HfbConst.AGENT_ID);
            paramMap.put("agentUserId", user.getMobile());
            paramMap.put("timestamp", System.currentTimeMillis());
            paramMap.put("sign", RequestHelper.getSign(paramMap));

            StringBuilder postdata = new StringBuilder();
            for (Map.Entry<String, Object> param : paramMap.entrySet()) {
                postdata.append(param.getKey()).append("=")
                        .append(param.getValue()).append("&");
            }
            byte[] respData = HttpUtils.doPost(BANK_QUERY_URL, postdata.toString().getBytes("utf-8"));
            if (respData == null || respData.length == 0) {
                log.warn("查询银行余额失败：银行服务未响应或未启动，userId={}", userId);
                return null;
            }
            String result = new String(respData, "utf-8");
            log.info("查询银行余额结果：userId={}, result={}", userId, result);
            return parseBankResponse(result);
        } catch (Exception e) {
            log.error("查询银行余额失败，userId={}", userId, e);
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> queryBankFlow(String date) {
        try {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("agentId", HfbConst.AGENT_ID);
            paramMap.put("date", date);
            paramMap.put("timestamp", System.currentTimeMillis());
            paramMap.put("sign", RequestHelper.getSign(paramMap));

            StringBuilder postdata = new StringBuilder();
            for (Map.Entry<String, Object> param : paramMap.entrySet()) {
                postdata.append(param.getKey()).append("=")
                        .append(param.getValue()).append("&");
            }
            byte[] respData = HttpUtils.doPost(BANK_FLOW_URL, postdata.toString().getBytes("utf-8"));
            if (respData == null || respData.length == 0) {
                log.warn("查询银行流水失败：银行服务未响应或未启动，date={}", date);
                return null;
            }
            String result = new String(respData, "utf-8");
            log.info("查询银行流水结果：date={}, result={}", date, result);
            return parseBankFlowResponse(result);
        } catch (Exception e) {
            log.error("查询银行流水失败，date={}", date, e);
            return null;
        }
    }

    private Map<String, Object> parseBankResponse(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isEmpty()) {
            return result;
        }
        String clean = json.replaceAll("[{}\"]", "");
        String[] pairs = clean.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                result.put(kv[0].trim(), kv[1].trim());
            }
        }
        return result;
    }

    private List<Map<String, Object>> parseBankFlowResponse(String json) {
        List<Map<String, Object>> flows = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return flows;
        }
        String clean = json.replaceAll("[{}\\[\\]\"]", "");
        String[] items = clean.split("\\},\\{");
        for (String item : items) {
            Map<String, Object> flow = new HashMap<>();
            String[] pairs = item.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    flow.put(kv[0].trim(), kv[1].trim());
                }
            }
            if (!flow.isEmpty()) {
                flows.add(flow);
            }
        }
        return flows;
    }

    private String getUserBindCode(Long userId) {
        try {
            User user = userService.getById(userId);
            if (user != null && user.getBindCode() != null) {
                return user.getBindCode();
            }
        } catch (Exception e) {
            log.error("获取用户bindCode失败，userId={}", userId, e);
        }
        return null;
    }

    private BigDecimal queryBankBalanceByBindCode(String bindCode) {
        try {
            BigDecimal amount = bankJdbcTemplate.queryForObject(
                    "SELECT amount FROM user_account WHERE user_code = ? AND is_deleted = 0",
                    BigDecimal.class, bindCode);
            log.debug("银行库查询余额，bindCode={}, amount={}", bindCode, amount);
            return amount;
        } catch (Exception e) {
            log.error("银行库查询余额失败，bindCode={}", bindCode, e);
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> findAbnormalAccounts() {
        try {
            List<Map<String, Object>> rows = bankJdbcTemplate.queryForList(
                    "SELECT user_code, amount, freeze_amount FROM user_account "
                            + "WHERE is_deleted = 0 AND (amount < 0 OR freeze_amount < 0)");
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("userCode", row.get("user_code"));
                m.put("amount", row.get("amount"));
                m.put("freezeAmount", row.get("freeze_amount"));
                result.add(m);
            }
            return result;
        } catch (Exception e) {
            log.error("扫描银行侧异常账户失败", e);
            return Collections.emptyList();
        }
    }

    private BigDecimal queryBankFreezeByBindCode(String bindCode) {
        try {
            BigDecimal freezeAmount = bankJdbcTemplate.queryForObject(
                    "SELECT freeze_amount FROM user_account WHERE user_code = ? AND is_deleted = 0",
                    BigDecimal.class, bindCode);
            return freezeAmount;
        } catch (Exception e) {
            log.error("银行库查询冻结金额失败，bindCode={}", bindCode, e);
            return null;
        }
    }
}