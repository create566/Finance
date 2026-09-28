package com.example.demo.jobhandler;

import com.example.demo.entity.Lend;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.service.LendReturnService;
import com.example.demo.service.LendService;
import com.example.demo.service.ReconciliationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 金融业务定时任务处理器
 *
 * 使用 Spring Task（启动类需加 @EnableScheduling）；
 * 生产环境可替换为 xxl-job 分布式任务调度。
 */
@Slf4j
@Component
public class FinanceJobHandler {

    @Autowired
    private LendReturnService lendReturnService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private LendService lendService;

    /**
     * 逾期扫描：每天凌晨 02:00 执行
     * cron = "秒 分 时 日 月 周"
     * 扫描已过还款日未还的还款计划：标记逾期 → 计罚息 → 标的置逾期 → 发送催收消息
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void overdueCheck() {
        log.info("【定时任务】逾期扫描开始......");
        try {
            int count = lendReturnService.overdueCheck();
            log.info("【定时任务】逾期扫描结束，处理逾期记录 {} 条", count);
        } catch (Exception e) {
            log.error("【定时任务】逾期扫描异常", e);
        }
    }

    /**
     * 银行对账：每天凌晨 03:30 执行
     * 核对平台本地 trans_flow 与旺旺银行侧流水是否一致
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void dailyReconciliation() {
        log.info("【定时任务】与旺旺银行对账开始......");
        try {
            String result = reconciliationService.reconcileWithBank(LocalDateTime.now());
            log.info("【定时任务】对账结束：{}", result);
        } catch (Exception e) {
            log.error("【定时任务】对账异常", e);
        }
    }

    /**
     * 流标扫描：每天凌晨 01:00 执行
     * 扫描"募资中"且已过募集截止时间(lendEndDate)的标的，执行流标处理：
     * 批量解冻投资人冻结资金 + 标的标记为已流标 + 发送通知
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void failExpiredLends() {
        log.info("【定时任务】流标扫描开始......");
        try {
            // lendEndDate 是 LocalDate（日期），必须用 LocalDate.now() 比较。
            // 之前用 LocalDateTime.now() 会按"当天 00:00"绑定时间戳比较，
            // 导致当天到期的标的在凌晨 1 点就被提前流标（少募集一整天）
            LocalDate today = LocalDate.now();
            // 查询所有募资中且截止日期已过的标的（排除 lendEndDate 为 null 的老数据）
            List<Lend> expiredLends = lendService.list(new LambdaQueryWrapper<Lend>()
                    .eq(Lend::getStatus, LendStatusEnum.FUNDING.getCode())
                    .isNotNull(Lend::getLendEndDate)
                    .lt(Lend::getLendEndDate, today));

            int count = 0;
            for (Lend lend : expiredLends) {
                try {
                    if (lendService.failLend(lend.getId())) {
                        count++;
                    }
                } catch (Exception e) {
                    // 单个标的流标失败不影响其他标的
                    log.error("标的 {} 流标处理失败", lend.getId(), e);
                }
            }
            log.info("【定时任务】流标扫描结束，处理流标 {} 个", count);
        } catch (Exception e) {
            log.error("【定时任务】流标扫描异常", e);
        }
    }
}
