package com.example.demo.service.impl;

import com.example.demo.service.BankDatabaseSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BankDatabaseSyncServiceImpl implements BankDatabaseSyncService {

    @Autowired
    @Qualifier("bankJdbcTemplate")
    private JdbcTemplate bankJdbcTemplate;

    @Override
    public void syncBindRecord(String bindCode, String agentUserId, String personalName,
                               String mobile, String idCard, String bankNo, String bankType) {
        try {
            Integer count = bankJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_bind WHERE bind_code = ? AND is_deleted = 0",
                    Integer.class, bindCode);
            if (count != null && count > 0) {
                log.info("银行库已存在bindCode={}，跳过插入", bindCode);
                return;
            }

            bankJdbcTemplate.update(
                    "INSERT INTO user_bind (agent_id, agent_user_id, personal_name, mobile, " +
                            "id_card, bank_no, bind_code, pay_passwd, status) " +
                            "VALUES (999888, ?, ?, ?, ?, ?, ?, '888888', 1)",
                    agentUserId, personalName, mobile, idCard, bankNo, bindCode);
            log.info("银行库user_bind插入成功，bindCode={}", bindCode);

            Integer accountCount = bankJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_account WHERE user_code = ? AND is_deleted = 0",
                    Integer.class, bindCode);
            if (accountCount == null || accountCount == 0) {
                bankJdbcTemplate.update(
                        "INSERT INTO user_account (user_code, amount, freeze_amount) VALUES (?, 0, 0)",
                        bindCode);
                log.info("银行库user_account插入成功，userCode={}", bindCode);
            }
        } catch (Exception e) {
            log.error("同步绑卡记录到银行库失败，bindCode={}", bindCode, e);
        }
    }

    @Override
    public boolean checkBindCodeExists(String bindCode) {
        try {
            Integer count = bankJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_bind WHERE bind_code = ? AND is_deleted = 0",
                    Integer.class, bindCode);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("查询银行库bindCode失败，bindCode={}", bindCode, e);
            return false;
        }
    }
}