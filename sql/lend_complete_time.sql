-- ============================================================
-- 满标时刻留痕：lend.complete_time（可重复执行）
--
-- 背景：
--   标的满标(COMPLETED)后，投资人资金即被存管银行冻结。若借款人不签《放款确认书》，
--   资金既不能放款也不能退回。合同模板已约定"满标后 3 个自然日内签署，逾期平台有权
--   终止撮合并退款"，因此需要定时任务自动处置 —— 而处置的**起算点必须明确且不可篡改**。
--
--   满标时目前只写了 update_time，而该列是 `on update CURRENT_TIMESTAMP`，
--   任何后续更新（放款写 payment_time、下架写 offline_time、后台编辑）都会覆盖它。
--   拿 update_time 当宽限期起算点，等于"动一下记录就重置宽限"，借款人拖着不签字
--   反而能无限续期 —— 用它做资金处置的触发条件是不安全的。故新增专用字段。
--
-- 字段语义：
--   complete_time  标的满标（募资金额达到目标）的时刻。仅在满标那一刻写入一次，
--                  之后任何操作都不得修改。NULL = 尚未满标 或 历史老数据未回填。
--
-- 用法：
--   mysql -uroot -p123456 -h127.0.0.1 ww_finance --default-character-set=utf8mb4 < sql/lend_complete_time.sql
-- ============================================================

-- ① 加列（幂等）
SET @exist_cnt := (SELECT COUNT(*)
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'lend'
                     AND COLUMN_NAME = 'complete_time');
SET @sql := IF(@exist_cnt = 0,
    'ALTER TABLE `lend` ADD COLUMN `complete_time` DATETIME NULL COMMENT ''满标时刻（3日签署宽限期的起算点）'' AFTER `payment_time`',
    'SELECT ''lend.complete_time 已存在，跳过'' AS msg');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ② 回填历史数据：加列之前就已满标的记录，用 update_time 作为近似起算点。
--    不精确，但比"NULL 起算点导致定时任务永远跳过、资金永久悬停"要好得多。
UPDATE `lend`
SET `complete_time` = `update_time`
WHERE `status` = 2
  AND `is_deleted` = 0
  AND `complete_time` IS NULL;

-- ---------------- 校验 ----------------
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'lend'
  AND COLUMN_NAME = 'complete_time';

SELECT status,
       COUNT(*)                                        AS total,
       SUM(CASE WHEN complete_time IS NULL THEN 1 ELSE 0 END) AS missing_start_point
FROM `lend`
WHERE `is_deleted` = 0
GROUP BY status
ORDER BY status;
