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

    /** 无法与本地流水比对的银行流水（缺少 transNo 等关键字段）在 trans_type 上的占位值。 */
    private static final int UNKNOWN_TRANS_TYPE = 0;

    @Autowired
    private TransFlowMapper transFlowMapper;

    @Autowired
    private TransFlowService transFlowService;

    @Autowired
    private BankAccountSyncService bankAccountSyncService;

    @Autowired
    private UserService userService;

    /**
     * 与银行对账。
     *
     * <p>该方法整体处于一个事务中：一次对账产生的差异记录要么全部落库、要么全部回滚，
     * 绝不能出现"写了一半、调用方却只看到一句异常文案"的静默部分提交。
     *
     * <p>历史 bug：这里原先把整个方法体包在 {@code try{...}catch(Exception e)} 中，
     * 捕获后仅 {@code log.error} 并把异常信息拼进返回值。由于方法声明了
     * {@code @Transactional(rollbackFor = Exception.class)}，被吞掉的异常不会触发回滚，
     * 事务照常提交已写入的差异记录 —— 注释与实现自相矛盾。现已移除该 catch，
     * 异常直接向上抛出触发回滚，由调用方 {@code FinanceJobHandler#dailyReconciliation}
     * （本身已有 try/catch + 日志）负责记录。
     *
     * <p>已知待优化（P2，非本次范围）：银行 HTTP 调用发生在数据库事务内部，
     * 会占用一个连接直到对账结束。若后续对账体量变大，应拆分为
     * 「非事务的取数 + 事务的落库」两步。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String reconcileWithBank(LocalDateTime date) {
        String targetDate = date != null ? date.format(DATE_FORMAT) : LocalDate.now().format(DATE_FORMAT);
        log.info("【对账开始】日期：{}", targetDate);

        int totalCount = 0;
        int matchCount = 0;
        int mismatchCount = 0;
        int missingCount = 0;
        int abnormalCount = 0;

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
        Map<String, TransFlow> localFlowMap = new LinkedHashMap<>();
        for (TransFlow flow : localFlows) {
            if (flow.getTransNo() == null) {
                log.warn("本地流水缺少 transNo，跳过比对：id={}", flow.getId());
                continue;
            }
            localFlowMap.put(flow.getTransNo(), flow);
        }

        // 4. 逐笔比对：银行流水 vs 本地流水
        for (Map<String, Object> bankFlow : bankFlows) {
            String bankTransNo = str(bankFlow.get("transNo"));
            BigDecimal bankAmount = parseAmount(bankFlow.get("amount"));
            totalCount++;

            // 银行流水缺少交易单号：无法定位到具体本地流水，单独计数。
            // 不写差异记录：reconciliation_record.trans_no 为 NOT NULL，写空值必然违反约束。
            if (bankTransNo == null) {
                abnormalCount++;
                log.error("银行流水缺少 transNo，无法比对：{}", bankFlow);
                continue;
            }

            TransFlow localFlow = localFlowMap.remove(bankTransNo);
            if (localFlow == null) {
                missingCount++;
                saveBankOnlyRecord(bankFlow, "本地无对应流水");
                continue;
            }

            if (bankAmount != null && localFlow.getTransAmount() != null
                    && bankAmount.compareTo(localFlow.getTransAmount()) == 0) {
                matchCount++;
            } else {
                mismatchCount++;
                saveMismatchRecord(bankTransNo, localFlow,
                        String.format("金额不一致（银行=%s，本地=%s）", bankAmount, localFlow.getTransAmount()));
            }
        }

        // 5. 本地有但银行没有的流水（本地多出）
        for (TransFlow localFlow : localFlowMap.values()) {
            missingCount++;
            saveLocalOnlyRecord(localFlow, "银行无对应流水");
        }

        // 6. 生成对账报告
        String summary = String.format("对账完成。总笔数：%d，匹配：%d，不一致：%d，缺失：%d，无法比对：%d",
                totalCount, matchCount, mismatchCount, missingCount, abnormalCount);
        log.info("【对账结果】{}", summary);

        if (mismatchCount > 0 || missingCount > 0 || abnormalCount > 0) {
            log.warn("对账发现异常，差异笔数：{}，无法比对：{}，需人工处理",
                    mismatchCount + missingCount, abnormalCount);
        }

        return summary;
    }

    /**
     * 金额不一致：以本地流水为准建档，同时记录银行侧单号与双方金额。
     */
    private void saveMismatchRecord(String bankTransNo, TransFlow localFlow, String reason) {
        ReconciliationRecord record = new ReconciliationRecord();
        record.setTransNo(localFlow.getTransNo());
        record.setTransType(localFlow.getTransType());
        record.setTransTypeName(typeNameOf(localFlow.getTransType()));
        record.setUserId(localFlow.getUserId());
        record.setUserName(localFlow.getUserName());
        record.setAmount(localFlow.getTransAmount());
        record.setBankTransNo(bankTransNo);
        record.setStatus(2); // 不一致
        record.setRemark(reason);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        this.save(record);
        log.warn("对账不一致：transNo={}, reason={}", localFlow.getTransNo(), reason);
    }

    /**
     * 本地多出：本地有流水、银行侧查无。
     */
    private void saveLocalOnlyRecord(TransFlow localFlow, String reason) {
        ReconciliationRecord record = new ReconciliationRecord();
        record.setTransNo(localFlow.getTransNo());
        record.setTransType(localFlow.getTransType());
        record.setTransTypeName(typeNameOf(localFlow.getTransType()));
        record.setUserId(localFlow.getUserId());
        record.setUserName(localFlow.getUserName());
        record.setAmount(localFlow.getTransAmount());
        record.setBankTransNo("N/A");
        record.setStatus(1); // 缺失
        record.setRemark(reason);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        this.save(record);
        log.warn("对账差异：transNo={}, reason={}", localFlow.getTransNo(), reason);
    }

    /**
     * 银行多出：银行有流水、本地查无。
     *
     * <p>该分支的字段全部来自银行接口返回，必须做 null 兜底：{@code trans_no} /
     * {@code trans_type} / {@code user_id} / {@code amount} 在表上都是 NOT NULL，
     * 之前直接用 {@code Integer.parseInt(bankFlow.get("type").toString())} 之类的写法，
     * 只要银行少回一个字段就会 NPE，而 NPE 又被外层 catch 吞掉，形成静默的部分提交。
     * 现在缺失字段用可读的占位值（0 / 0L / 0.00）落库，并在 remark 与日志中体现。
     */
    private void saveBankOnlyRecord(Map<String, Object> bankFlow, String reason) {
        String bankTransNo = str(bankFlow.get("transNo"));
        Integer bankType = parseInt(bankFlow.get("type"));
        Long bankUserId = parseLong(bankFlow.get("userId"));
        BigDecimal bankAmount = parseAmount(bankFlow.get("amount"));

        ReconciliationRecord record = new ReconciliationRecord();
        record.setTransNo(bankTransNo);
        record.setTransType(bankType != null ? bankType : UNKNOWN_TRANS_TYPE);
        String typeName = str(bankFlow.get("typeName"));
        record.setTransTypeName(typeName != null ? typeName : typeNameOf(bankType));
        record.setUserId(bankUserId != null ? bankUserId : 0L);
        record.setAmount(bankAmount != null ? bankAmount : BigDecimal.ZERO);
        record.setBankTransNo(bankTransNo);
        record.setStatus(1); // 缺失（本地无）
        record.setRemark(reason);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        this.save(record);
        log.warn("对账缺失：bankTransNo={}, reason={}", bankTransNo, reason);
    }

    // ------------------------------------------------------------------
    // 解析辅助：银行接口返回的是 Map<String,Object>，字段可能缺失或类型不符，
    // 一律走 null 安全解析，避免一个脏字段让整次对账失败。
    // ------------------------------------------------------------------

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal parseAmount(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    /**
     * 交易类型名称。不再抛异常（{@link TransTypeEnum#fromCode} 遇到未知码会抛
     * IllegalArgumentException，同样会被外层 catch 吞掉），未知码返回可读文案。
     */
    private static String typeNameOf(Integer code) {
        if (code == null) {
            return "未知类型";
        }
        for (TransTypeEnum type : TransTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type.getDesc();
            }
        }
        return "未知类型(" + code + ")";
    }
}
