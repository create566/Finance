package com.example.demo.jobhandler;

import com.example.demo.entity.Contract;
import com.example.demo.entity.Lend;
import com.example.demo.enums.ContractBizTypeEnum;
import com.example.demo.enums.ContractStatusEnum;
import com.example.demo.enums.ContractTypeEnum;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.service.ContractService;
import com.example.demo.service.LendReturnService;
import com.example.demo.service.LendService;
import com.example.demo.service.ReconciliationService;
import com.example.demo.utils.RedisDistributedLock;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 金融业务定时任务处理器
 *
 * <p>使用 Spring Task（启动类需加 @EnableScheduling）；
 * 生产环境可替换为 xxl-job 分布式任务调度。</p>
 *
 * <p><b>2026-10-06：全部任务加 Redis 分布式锁。</b>
 * 多实例部署时 Spring Task 会在每个实例各跑一遍，其中「超期未签确认书自动退资」是动钱动作，
 * 重复执行意味着重复退资。因此这里每个任务执行前先抢锁，抢不到就跳过本次。</p>
 *
 * <p><b>Redis 不可达时的策略是「跳过」而不是「放行」</b>：拿不到锁无法证明没有其他实例在执行，
 * 宁可少跑一次（下次补上），也不能冒着重复动钱的风险执行。</p>
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

    @Autowired
    private ContractService contractService;

    @Autowired
    private RedisDistributedLock redisDistributedLock;

    /**
     * 分布式锁总开关。本地单实例开发可置 false 关闭（跳过抢锁，行为与改造前一致）。
     * 生产多实例部署必须保持 true。
     */
    @Value("${job.distributed-lock.enabled:true}")
    private boolean distributedLockEnabled;

    /**
     * 带分布式锁执行任务
     *
     * @param lockName      锁名，业务唯一
     * @param expireSeconds 锁过期时间（秒），需大于任务最大执行时长
     * @param task          任务本体
     */
    private void runWithLock(String lockName, long expireSeconds, Runnable task) {
        if (!distributedLockEnabled) {
            task.run();
            return;
        }
        String token;
        try {
            token = redisDistributedLock.tryLock(lockName, expireSeconds);
        } catch (IllegalStateException e) {
            // Redis 不可达：跳过本次执行，避免多实例重复动钱（详见类注释）
            log.error("【定时任务】{} 本次跳过：无法获取分布式锁（Redis 不可达）", lockName);
            return;
        }
        if (token == null) {
            log.info("【定时任务】{} 本次跳过：已有实例在执行", lockName);
            return;
        }
        try {
            task.run();
        } finally {
            redisDistributedLock.unlock(lockName, token);
        }
    }

    /**
     * 逾期扫描：每天凌晨 02:00 执行
     * cron = "秒 分 时 日 月 周"
     * 扫描已过还款日未还的还款计划：标记逾期 → 计罚息 → 标的置逾期 → 发送催收消息
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void overdueCheck() {
        runWithLock("overdueCheck", 1800, () -> {
            log.info("【定时任务】逾期扫描开始......");
            try {
                int count = lendReturnService.overdueCheck();
                log.info("【定时任务】逾期扫描结束，处理逾期记录 {} 条", count);
            } catch (Exception e) {
                log.error("【定时任务】逾期扫描异常", e);
            }
        });
    }

    /**
     * 银行对账：每天凌晨 03:30 执行
     * 核对平台本地 trans_flow 与旺旺银行侧流水是否一致
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void dailyReconciliation() {
        runWithLock("dailyReconciliation", 3600, () -> {
            log.info("【定时任务】与旺旺银行对账开始......");
            try {
                String result = reconciliationService.reconcileWithBank(LocalDateTime.now());
                log.info("【定时任务】对账结束：{}", result);
            } catch (Exception e) {
                log.error("【定时任务】对账异常", e);
            }
        });
    }

    /**
     * 流标扫描：每天凌晨 01:00 执行
     * 扫描"募资中"且已过募集截止时间(lendEndDate)的标的，执行流标处理：
     * 批量解冻投资人冻结资金 + 标的标记为已流标 + 发送通知
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void failExpiredLends() {
        runWithLock("failExpiredLends", 1800, () -> {
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
        });
    }

    /**
     * 超期未签《放款确认书》自动退资：每天凌晨 01:30 执行
     *
     * <p>场景：标的满标(COMPLETED)后投资人资金被存管银行冻结，借款人迟迟不签《放款确认书》，
     * 则既不能放款也不能退回，形成资金无限期悬停（监管口径下的资金池风险，合规红线）。
     * 《借款合同》/《出借协议》模板已事先约定"满标后 3 个自然日内签署，逾期平台有权
     * 终止撮合并退款"，本任务就是把这条约定执行下去。</p>
     *
     * <p><b>三个必须守住的边界</b>（漏一条就会误伤正常业务）：</p>
     * <ol>
     *   <li>起算点用 {@code complete_time}（满标时刻），<b>不能用 update_time</b> —— 后者是
     *       {@code on update CURRENT_TIMESTAMP}，任何后续更新都会重置宽限期。</li>
     *   <li>只处理"《放款确认书》未签署"的标的：已签但运营还没点放款的，属于正常待放款，
     *       不能退资。</li>
     *   <li>已发起放款（{@code payment_admin_id} 非空）的跳过：钱在路上，退资会导致账目错乱。</li>
     * </ol>
     */
    @Scheduled(cron = "0 30 1 * * ?")
    public void refundUnsignedCompletedLends() {
        // 锁过期时间给到 30 分钟：退资要逐个标的调用银行解冻，实例多时可能较慢，
        // 但必须留足余量，否则锁提前失效会让另一个实例进来重复退资。
        runWithLock("refundUnsignedCompletedLends", 1800, () -> {
            log.info("【定时任务】超期未签确认书扫描开始......");
            try {
                // 宽限期起算点 = 满标时刻；超过 3 个自然日仍未签即触发
                LocalDateTime deadline = LocalDateTime.now()
                        .minusDays(LendService.MAKE_LOAN_SIGN_DEADLINE_DAYS);
                List<Lend> candidates = lendService.list(new LambdaQueryWrapper<Lend>()
                        .eq(Lend::getStatus, LendStatusEnum.COMPLETED.getCode())
                        // complete_time 为 NULL 的历史老数据不处置：起算点不明就动投资人的钱，
                        // 风险远大于收益，宁可留着由人工在管理端判断。
                        .isNotNull(Lend::getCompleteTime)
                        .lt(Lend::getCompleteTime, deadline));

                int count = 0;
                for (Lend lend : candidates) {
                    try {
                        if (!isPaymentContractUnsigned(lend.getId())) {
                            log.info("标的 {} 的《放款确认书》已签署，跳过自动退资", lend.getId());
                            continue;
                        }
                        if (lend.getPaymentAdminId() != null) {
                            log.warn("标的 {} 已发起放款(paymentAdminId={})，跳过自动退资，避免资金账目错乱",
                                    lend.getId(), lend.getPaymentAdminId());
                            continue;
                        }
                        if (lendService.offlineBySystem(lend.getId())) {
                            count++;
                        }
                    } catch (Exception e) {
                        // 单个标的处置失败不影响其他标的
                        log.error("标的 {} 超期退资失败", lend.getId(), e);
                    }
                }
                log.info("【定时任务】超期未签确认书扫描结束，自动退款 {} 个标的", count);
            } catch (Exception e) {
                log.error("【定时任务】超期未签确认书扫描异常", e);
            }
        });
    }

    /**
     * 判断某标的的《放款确认书》是否尚未签署。
     *
     * <p>已签署（status=1）返回 false，未签或合同不存在返回 true。
     * 合同不存在按"未签"处理是安全的：没签过确认书，本来就该退资。</p>
     */
    private boolean isPaymentContractUnsigned(Long lendId) {
        List<Contract> contracts = contractService.list(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getBizType, ContractBizTypeEnum.LEND.getCode())
                .eq(Contract::getBizId, lendId)
                .eq(Contract::getContractType, ContractTypeEnum.PAYMENT.getCode()));
        return contracts.stream()
                .noneMatch(c -> ContractStatusEnum.SIGNED.getCode().equals(c.getStatus()));
    }
}
