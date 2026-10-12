-- ============================================================================
-- fix_stale_pending_flows.sql
-- 释放「悬挂的在途流水」，让被永久占用的可用余额归位。
--
-- 【症状】用户银行账户里明明有钱，投标却提示
--     「可用余额不足，本次需 ¥X，当前可用 ¥0.00」
--
-- 【成因】可用额的算式是
--     getAvailableBalance = 银行账户 amount − Σ(status=0 且 trans_type∈{提现2,投标3,还款6} 的流水)
--   （BankAccountSyncServiceImpl.getAvailableBalance）
--   而「投标 / 还款 / 提现 / 充值」都是在**生成银行表单的那一刻**就落一条 status=0(PROCESSING)
--   占位流水（LendItemServiceImpl:252 / LendReturnServiceImpl:700 / UserAccountServiceImpl:109,156），
--   只有银行异步回调 settlePending 才会把它结算成终态。
--   若用户压根没走完银行页（银行侧无对应记录、自然也没有回调），这条占位就**永久挂着**，
--   表现为「钱够却提示余额不足」。代码侧已补「超时自动释放」定时任务（FinanceJobHandler），
--   本脚本用于把**已经存在的**脏数据一次性清掉。
--
-- 【判据】只碰「已生成 ≥ 3 分钟仍停在处理中」的扣款类流水。
--   正常银行页交互是秒级的；3 分钟仍未结算，说明这单已经被用户放弃。
--   实测这两条在银行库 ww_bank.user_invest 里查无对应 agent_bill_no，确认资金从未移动。
--
-- 【幂等】可重复执行：第二次执行时没有 status=0 的行可改，影响 0 行。
-- 【可回滚】改动前把原行整表备份到 trans_flow_bak_stale_20261010。
-- ============================================================================

-- ---------------------------------------------------------------- 0. 改前快照
SELECT '=== 改前：待释放的在途流水 ===' AS step;
SELECT id, user_id, trans_no, trans_type, trans_type_name, trans_amount, status, memo, create_time,
       TIMESTAMPDIFF(MINUTE, create_time, NOW()) AS age_min
FROM trans_flow
WHERE status = 0 AND is_deleted = 0 AND trans_type IN (2, 3, 6)
  AND create_time < DATE_SUB(NOW(), INTERVAL 3 MINUTE)
ORDER BY id;

-- ---------------------------------------------------------------- 1. 备份（幂等：先删后建）
DROP TABLE IF EXISTS trans_flow_bak_stale_20261010;
CREATE TABLE trans_flow_bak_stale_20261010 AS
SELECT * FROM trans_flow
WHERE status = 0 AND is_deleted = 0 AND trans_type IN (2, 3, 6)
  AND create_time < DATE_SUB(NOW(), INTERVAL 3 MINUTE);

-- ---------------------------------------------------------------- 2. 释放占位
-- 置为「失败(2)」而不是删除：保留审计痕迹，且 settlePending 允许 FAILED→SUCCESS，
-- 万一银行回调迟到，仍能被正确结算入账（见 TransFlowServiceImpl.existsSettledByTransNo）。
UPDATE trans_flow
SET status      = 2,
    memo        = CONCAT(IFNULL(memo, ''), '（超时未收到银行回调，自动释放占额）'),
    update_time = NOW()
WHERE status = 0 AND is_deleted = 0 AND trans_type IN (2, 3, 6)
  AND create_time < DATE_SUB(NOW(), INTERVAL 3 MINUTE);

-- ---------------------------------------------------------------- 3. 改后复核
SELECT '=== 改后：该用户仍残留的在途流水（应为空）===' AS step;
SELECT id, user_id, trans_no, trans_type, trans_amount, status, memo, create_time
FROM trans_flow
WHERE status = 0 AND is_deleted = 0 AND trans_type IN (2, 3, 6)
ORDER BY id;

SELECT '=== 改后：被释放的流水 ===' AS step;
SELECT id, user_id, trans_no, trans_type, trans_amount, status, memo, update_time
FROM trans_flow
WHERE id IN (SELECT id FROM trans_flow_bak_stale_20261010)
ORDER BY id;

-- ---------------------------------------------------------------- 4. 可用额算式复核（示例 user 100）
SELECT '=== user 100 可用额算式（银行 amount − 在途）===' AS step;
SELECT u.id AS user_id,
       p.amount                                                AS 平台镜像,
       (SELECT IFNULL(SUM(t.trans_amount), 0) FROM trans_flow t
         WHERE t.user_id = u.id AND t.status = 0 AND t.is_deleted = 0
           AND t.trans_type IN (2, 3, 6))                      AS 在途占额,
       p.amount - (SELECT IFNULL(SUM(t.trans_amount), 0) FROM trans_flow t
                   WHERE t.user_id = u.id AND t.status = 0 AND t.is_deleted = 0
                     AND t.trans_type IN (2, 3, 6))             AS 预计可用额
FROM user u LEFT JOIN user_account p ON p.user_id = u.id
WHERE u.id = 100;
