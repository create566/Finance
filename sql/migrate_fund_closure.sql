-- ============================================================================
-- 资金闭环改造 · 平台库表结构迁移（ww_finance）
--
-- 目标：
--   ① 用数据库唯一键把两条「资金闭环恒等式」钉死，任何代码写错都会被拒：
--        · lend_item_return(lend_return_id, lend_item_id) 唯一  → 恒等式⑨「分期明细无重复行」
--        · lend_return(lend_id, current_period)            唯一  → 每一标的每一期只有一行
--      为什么必须落到约束上：放款落账、还款回调、对账修补都是**并发入口**，
--      只靠应用层"先查再插"挡不住（查与插之间就是竞态窗口），
--      而一旦出现重复行，银行会按两份明细重复打款。
--
--   ② 利率精度 decimal(10,2) → decimal(10,6)：
--      (10,2) 的最小刻度是 0.01 个百分点。1 亿元本金按 0.01% 计的利息差就是 10,000 元/年，
--      而还款计划是按利率逐期算的 ⇒ 利率四舍五入会直接放大到每期还款额上。
--      放大到 6 位小数后，换算成年化利率的绝对误差 < 1e-6 个百分点。
--      纯扩位，不改变已有数据的数值，可安全回退（再 MODIFY 回 (10,2) 即可，但会截断新增小数）。
--
-- 执行前提：已确认「无重复行」「无逻辑删除行」（见文件末尾的自检语句）。
-- 幂等性：脚本可重复执行（唯一键已存在则跳过；MODIFY 天然幂等）。
--
-- 用法：
--   "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p123456 \
--     --default-character-set=utf8mb4 ww_finance < sql/migrate_fund_closure.sql
-- ============================================================================

-- ---------- 手术前自检：任一非 0 都必须先停下来清理数据 ----------
SELECT '【自检1】分摊明细重复行（必须为 0 行，否则唯一键加不上）' AS check_item;
SELECT lend_return_id, lend_item_id, COUNT(*) AS dup_rows
FROM lend_item_return WHERE is_deleted = 0
GROUP BY lend_return_id, lend_item_id HAVING COUNT(*) > 1;

SELECT '【自检2】同一标的同一期重复行（必须为 0 行）' AS check_item;
SELECT lend_id, current_period, COUNT(*) AS dup_rows
FROM lend_return WHERE is_deleted = 0
GROUP BY lend_id, current_period HAVING COUNT(*) > 1;

-- ---------- ① lend_item_return：分期明细唯一键 ----------
SET @exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'lend_item_return'
                  AND INDEX_NAME = 'uk_lr_item');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE lend_item_return ADD UNIQUE KEY uk_lr_item (lend_return_id, lend_item_id)',
    'SELECT ''uk_lr_item 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- ② lend_return：标的+期次唯一键 ----------
SET @exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'lend_return'
                  AND INDEX_NAME = 'uk_lend_period');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE lend_return ADD UNIQUE KEY uk_lend_period (lend_id, current_period)',
    'SELECT ''uk_lend_period 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- ③ 利率列扩位到 6 位小数 ----------
ALTER TABLE lend             MODIFY lend_year_rate   decimal(10,6) DEFAULT NULL COMMENT '年化利率(百分数,7.000000 表示 7%)';
ALTER TABLE lend             MODIFY service_rate     decimal(10,6) DEFAULT NULL COMMENT '平台服务费率(百分数)';
ALTER TABLE lend_item        MODIFY lend_year_rate   decimal(10,6) DEFAULT NULL COMMENT '年化利率(百分数)';
ALTER TABLE lend_return      MODIFY lend_year_rate   decimal(10,6) DEFAULT NULL COMMENT '年化利率(百分数)';
ALTER TABLE lend_item_return MODIFY lend_year_rate   decimal(10,6) DEFAULT NULL COMMENT '年化利率(百分数)';
ALTER TABLE borrow_info      MODIFY borrow_year_rate decimal(10,6) DEFAULT NULL COMMENT '年化利率(百分数)';
ALTER TABLE contract         MODIFY lend_year_rate   decimal(10,6) DEFAULT NULL COMMENT '年化利率(百分数)';

-- ---------- 手术后核验 ----------
SELECT '【核验1】唯一键' AS verify_item, TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND INDEX_NAME IN ('uk_lr_item', 'uk_lend_period')
GROUP BY TABLE_NAME, INDEX_NAME;

SELECT '【核验2】利率列精度' AS verify_item, TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, NUMERIC_SCALE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME IN ('lend_year_rate', 'service_rate', 'borrow_year_rate')
ORDER BY TABLE_NAME;
