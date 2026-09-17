package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.Bank.HfbConst;
import com.example.demo.Bank.RequestHelper;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAccount;
import com.example.demo.mapper.UserAccountMapper;
import com.example.demo.service.BankAccountSyncService;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserService;
import com.wwfinance.common.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final String BANK_QUERY_URL = "http://localhost:9090/userAccount/queryBalance";
    private static final String BANK_FLOW_URL = "http://localhost:9090/userAccount/queryFlow";

    @Override
    public BigDecimal getBankBalance(Long userId) {
        try {
            Map<String, Object> result = queryBankAccount(userId);
            if (result != null && result.get("amount") != null) {
                return new BigDecimal(result.get("amount").toString());
            }
        } catch (Exception e) {
            log.error("查询银行余额失败，userId={}", userId, e);
        }
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getBankFreezeAmount(Long userId) {
        try {
            Map<String, Object> result = queryBankAccount(userId);
            if (result != null && result.get("freezeAmount") != null) {
                return new BigDecimal(result.get("freezeAmount").toString());
            }
        } catch (Exception e) {
            log.error("查询银行冻结金额失败，userId={}", userId, e);
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

            UserAccount account = userAccountMapper.selectById(userId);
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
}
