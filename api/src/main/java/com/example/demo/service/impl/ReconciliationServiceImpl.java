package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.*;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.mapper.*;
import com.example.demo.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class ReconciliationServiceImpl extends ServiceImpl<ReconciliationRecordMapper, ReconciliationRecord>
        implements ReconciliationService {

    private static final String BANK_FLOW_URL = "http://localhost:9090/userAccount/queryFlow";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private TransFlowMapper transFlowMapper;

    @Autowired
    private TransFlowService transFlowService;

    @Autowired
    private BankAccountSyncService bankAccountSyncService;

    @Autowired
    private UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String reconcileWithBank(LocalDateTime date) {
        String targetDate = date != null ? date.format(DATE_FORMAT) : LocalDate.now().format(DATE_FORMAT);
        log.info("【对账开始】日期：{}", targetDate);

        int totalCount = 0;
        int matchCount = 0;
        int mismatchCount = 0;
        int missingCount = 0;
        StringBuilder summary = new StringBuilder();

        try {
            // 1. 从银行获取当日流水（调用银行API）
            List<Map<String, Object>> bankFlows = bankAccountSyncService.queryBankFlow(targetDate);
            log.info("银行返回流水条数：{}", bankFlows != null ? bankFlows.size() : 0);

            if (bankFlows == null || bankFlows.isEmpty()) {
                log.warn("银行流水为空，可能为对账异常");
                return "对账异常：银行流水为空";
            }

            // 2. 查询本地当日所有交易流水
            LambdaQueryWrapper<TransFlow> localWrapper = new LambdaQueryWrapper<>();
            localWrapper.ge(TransFlow::getCreateTime, targetDate + " 00:00:00")
                    .le(TransFlow::getCreateTime, targetDate + " 23:59:59")
                    .eq(TransFlow::getDeleted, false);
            List<TransFlow> localFlows = transFlowMapper.selectList(localWrapper);
            log.info("本地流水条数：{}", localFlows.size());

            // 3. 构建本地流水 Map（key: transNo）
            Map<String, TransFlow> localFlowMap = new HashMap<>();
            for (TransFlow flow : localFlows) {
                localFlowMap.put(flow.getTransNo(), flow);
            }

            // 4. 逐笔比对：银行流水 vs 本地流水
            for (Map<String, Object> bankFlow : bankFlows) {
                String bankTransNo = (String) bankFlow.get("transNo");
                BigDecimal bankAmount = new BigDecimal(bankFlow.get("amount").toString());
                String bankType = (String) bankFlow.get("type");

                totalCount++;

                boolean found = false;
                for (Iterator<Map.Entry<String, TransFlow>> it = localFlowMap.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, TransFlow> entry = it.next();
                    TransFlow localFlow = entry.getValue();
                    if (bankTransNo.equals(localFlow.getTransNo())) {
                        found = true;
                        if (bankAmount.compareTo(localFlow.getTransAmount()) == 0) {
                            matchCount++;
                        } else {
                            mismatchCount++;
                            saveMismatchRecord(bankFlow, localFlow, "金额不一致");
                        }
                        it.remove();
                        break;
                    }
                }

                if (!found) {
                    missingCount++;
                    saveMissingRecord(bankFlow, "本地无对应流水");
                }
            }

            // 5. 本地有但银行没有的流水（本地多出）
            for (Map.Entry<String, TransFlow> entry : localFlowMap.entrySet()) {
                TransFlow localFlow = entry.getValue();
                missingCount++;
                saveMissingRecord(null, localFlow, "银行无对应流水");
            }

            // 6. 生成对账报告
            summary.append(String.format("对账完成。总笔数：%d，匹配：%d，不一致：%d，缺失：%d",
                    totalCount, matchCount, mismatchCount, missingCount));
            log.info("【对账结果】{}", summary);

            if (mismatchCount > 0 || missingCount > 0) {
                log.warn("对账发现异常，差异笔数：{}，需人工处理", mismatchCount + missingCount);
            }

        } catch (Exception e) {
            log.error("【对账异常】", e);
            summary.append("对账异常：").append(e.getMessage());
        }

        return summary.toString();
    }

    private void saveMismatchRecord(Map<String, Object> bankFlow, TransFlow localFlow, String reason) {
        ReconciliationRecord record = new ReconciliationRecord();
        record.setTransNo(localFlow.getTransNo());
        record.setTransType(localFlow.getTransType());
        record.setTransTypeName(TransTypeEnum.fromCode(localFlow.getTransType()).getDesc());
        record.setUserId(localFlow.getUserId());
        record.setUserName(localFlow.getUserName());
        record.setAmount(localFlow.getTransAmount());
        record.setBankTransNo(bankFlow != null ? (String) bankFlow.get("transNo") : "N/A");
        record.setStatus(2); // 不一致
        record.setRemark(reason);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        this.save(record);
        log.warn("对账不一致：transNo={}, reason={}", localFlow.getTransNo(), reason);
    }

    private void saveMissingRecord(Map<String, Object> bankFlow, TransFlow localFlow, String reason) {
        ReconciliationRecord record = new ReconciliationRecord();
        record.setTransNo(localFlow.getTransNo());
        record.setTransType(localFlow.getTransType());
        record.setTransTypeName(TransTypeEnum.fromCode(localFlow.getTransType()).getDesc());
        record.setUserId(localFlow.getUserId());
        record.setUserName(localFlow.getUserName());
        record.setAmount(localFlow.getTransAmount());
        record.setBankTransNo(bankFlow != null ? (String) bankFlow.get("transNo") : "N/A");
        record.setStatus(1); // 缺失
        record.setRemark(reason);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        this.save(record);
        log.warn("对账差异：transNo={}, reason={}", localFlow.getTransNo(), reason);
    }

    private void saveMissingRecord(Map<String, Object> bankFlow, String reason) {
        ReconciliationRecord record = new ReconciliationRecord();
        record.setTransNo((String) bankFlow.get("transNo"));
        record.setTransType(Integer.parseInt(bankFlow.get("type").toString()));
        record.setTransTypeName((String) bankFlow.get("typeName"));
        record.setUserId(Long.parseLong(bankFlow.get("userId").toString()));
        record.setAmount(new BigDecimal(bankFlow.get("amount").toString()));
        record.setBankTransNo((String) bankFlow.get("transNo"));
        record.setStatus(1); // 缺失（本地无）
        record.setRemark(reason);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        this.save(record);
        log.warn("对账缺失：bankTransNo={}, reason={}", bankFlow.get("transNo"), reason);
    }
}
