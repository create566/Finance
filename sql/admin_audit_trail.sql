-- ============================================================
-- 审核 / 放款留痕补齐（可重复执行）
--
-- 背景：lend 表预留了 check_time / check_admin_id / payment_admin_id 三个字段，
--       建表脚本里也有，但全项目从未写入过，属于"死字段"；
--       borrow_info 表更是连审核人字段都没有。
--       结果是"这笔标的是谁、什么时候批的"在库里查不到 —— 合规上的真实缺口。
--
-- 本脚本做四件事：
--   ① 修正 lend.payment_admin_id 的实际类型（历史 DDL 漂移，见下）
--   ② 给 borrow_info 补 check_admin_id / check_time
--   ③ 用 update_time / publish_date 回填历史数据，让老记录也有留痕
--   ④ 校验结果
--
-- 用法：
--   mysql -uroot -p123456 -h127.0.0.1 ww_finance --default-character-set=utf8mb4 < sql/admin_audit_trail.sql
-- ============================================================

-- ① 修正 lend.payment_admin_id 的类型
--    建表脚本（db_init.sql / sql/lend.sql / sql/fix_lend_tables.sql）都定义为 bigint(20)，
--    但线上库该列实际是 datetime。实体 Lend.paymentAdminId 声明为 Long，
--    一旦开始写入就会遇到"bigint 塞进 datetime 列"的错误 —— 必须先把类型对齐。
--    该列当前全为 NULL，改类型无数据风险。
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'lend'
      AND COLUMN_NAME = 'payment_admin_id'
      AND DATA_TYPE <> 'bigint') > 0,
  'ALTER TABLE `lend` MODIFY COLUMN `payment_admin_id` BIGINT NULL COMMENT ''放款发起管理员ID''',
  'SELECT ''lend.payment_admin_id 类型已是 bigint，跳过'' AS msg'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 同时给 check_admin_id 兜一层：若某些库该列缺失或类型不对，一并补齐
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'lend'
      AND COLUMN_NAME = 'check_admin_id') = 0,
  'ALTER TABLE `lend` ADD COLUMN `check_admin_id` BIGINT NULL COMMENT ''上架审核管理员ID'' AFTER `status`',
  IF((SELECT DATA_TYPE FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lend'
         AND COLUMN_NAME = 'check_admin_id') <> 'bigint',
     'ALTER TABLE `lend` MODIFY COLUMN `check_admin_id` BIGINT NULL COMMENT ''上架审核管理员ID''',
     'SELECT ''lend.check_admin_id 已就绪，跳过'' AS msg')));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'lend'
      AND COLUMN_NAME = 'check_time') = 0,
  'ALTER TABLE `lend` ADD COLUMN `check_time` DATETIME NULL COMMENT ''上架审核时间'' AFTER `status`',
  'SELECT ''lend.check_time 已就绪，跳过'' AS msg'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ② borrow_info 补审核留痕
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'borrow_info'
      AND COLUMN_NAME = 'check_admin_id') = 0,
  'ALTER TABLE `borrow_info` ADD COLUMN `check_admin_id` BIGINT NULL COMMENT ''审核管理员ID'' AFTER `status`',
  'SELECT ''borrow_info.check_admin_id 已就绪，跳过'' AS msg'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'borrow_info'
      AND COLUMN_NAME = 'check_time') = 0,
  'ALTER TABLE `borrow_info` ADD COLUMN `check_time` DATETIME NULL COMMENT ''审核时间'' AFTER `check_admin_id`',
  'SELECT ''borrow_info.check_time 已就绪，跳过'' AS msg'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ③ 历史数据回填 ------------------------------------------------------------------
-- 只填 check_time，不回填 check_admin_id（当年的操作人已经无从考证，宁可为空也不要编造）。
-- 说明：这里用 update_time 作为审核时间的近似值 —— borrow_info 审核时唯一被改写的就是这个字段。

UPDATE `borrow_info`
   SET `check_time` = `update_time`
 WHERE `status` IN (2, -1)
   AND `check_time` IS NULL;

UPDATE `lend`
   SET `check_time` = COALESCE(`publish_date`, `update_time`)
 WHERE `status` IN (1, 2, 5, 6)
   AND `check_time` IS NULL
   AND COALESCE(`publish_date`, `update_time`) IS NOT NULL;

-- ④ 校验 -------------------------------------------------------------------------
SELECT 'borrow_info' AS tbl, COUNT(*) AS total,
       SUM(`check_time` IS NOT NULL) AS has_check_time,
       SUM(`check_admin_id` IS NOT NULL) AS has_admin
  FROM `borrow_info`
UNION ALL
SELECT 'lend', COUNT(*),
       SUM(`check_time` IS NOT NULL),
       SUM(`check_admin_id` IS NOT NULL)
  FROM `lend`;

-- ---------- 回滚（如需）----------
-- ALTER TABLE `borrow_info` DROP COLUMN `check_time`, DROP COLUMN `check_admin_id`;
-- UPDATE `lend` SET `check_time` = NULL, `check_admin_id` = NULL, `payment_admin_id` = NULL;
