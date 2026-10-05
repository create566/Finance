-- ============================================================
-- 下架处置留痕补齐（可重复执行）
--
-- 背景：
--   offline() 与 failLend() 共用 withdrawAllInvestments()，都涉及处置投资人被冻结的资金。
--   failLend 是定时任务（系统行为，天然无操作人），offline() 是管理端人工操作 ——
--   后者此前完全没有留痕：库里查不出"这笔钱是谁退的、什么时候退的"。
--   处置投资人资金属于高风险动作，审计留痕是合规底线。
--
--   同时也补上一处不对称：lend 已有 check_*（上架审核）、payment_*（放款）两组留痕字段，
--   唯独"退出 / 处置"这组没有。
--
-- 字段语义（务必看清，别与 check_* 混淆）：
--   offline_admin_id  下架操作人。非空 = 管理员人工下架；NULL = 系统自动处置
--   offline_time      下架处置时间（每次下架都会覆盖写，记录的是"最后一次处置"）
--
-- 用法：
--   mysql -uroot -p123456 -h127.0.0.1 ww_finance --default-character-set=utf8mb4 < sql/lend_offline_audit.sql
-- ============================================================

SET @exist_cnt := (SELECT COUNT(*)
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'lend'
                     AND COLUMN_NAME = 'offline_admin_id');
SET @sql := IF(@exist_cnt = 0,
    'ALTER TABLE `lend` ADD COLUMN `offline_admin_id` BIGINT NULL COMMENT ''下架操作管理员ID（NULL=系统自动处置）'' AFTER `payment_admin_id`',
    'SELECT ''lend.offline_admin_id 已存在，跳过'' AS msg');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @exist_cnt := (SELECT COUNT(*)
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'lend'
                     AND COLUMN_NAME = 'offline_time');
SET @sql := IF(@exist_cnt = 0,
    'ALTER TABLE `lend` ADD COLUMN `offline_time` DATETIME NULL COMMENT ''下架处置时间'' AFTER `offline_admin_id`',
    'SELECT ''lend.offline_time 已存在，跳过'' AS msg');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------- 校验：四组留痕字段应齐备 ----------------
SELECT COLUMN_NAME,
       DATA_TYPE,
       IS_NULLABLE,
       COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'lend'
  AND COLUMN_NAME IN ('check_admin_id', 'check_time',
                      'payment_admin_id', 'payment_time',
                      'offline_admin_id', 'offline_time')
ORDER BY ORDINAL_POSITION;
