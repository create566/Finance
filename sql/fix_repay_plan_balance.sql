-- 【P4-53】存量还款计划金额订正（自动生成于 2026-10-08）
-- 本文件仅记录 ③ 明细倒挤部分；①② 为集合式 UPDATE，见脚本内注释。
-- 背景：还款计划分摊各自四舍五入到分，导致
--   ① lend_return.principal + interest != total
--   ② Σ lend_item_return.total != lend_return.total
-- 银行 returnCommit 有强等式 voteFeeAmt + ΣtransitAmt == totalAmt，差 1 分即拒绝还款
-- ⇒ 多投资人标的的还款全部失败。
-- 订正口径与代码一致：total = 已舍入本金 + 已舍入利息；明细倒挤到投资金额最大的一户。
-- ⚠️ 只动 status=0（未还）的期，已还期保持原样。

START TRANSACTION;

-- ① 期供自身：total := principal + interest
UPDATE lend_return SET total = principal + interest WHERE status = 0 AND principal + interest <> total;

-- ② 投资人明细倒挤
UPDATE lend_item_return SET total = principal + interest WHERE status = 0 AND total <> principal + interest;

COMMIT;
