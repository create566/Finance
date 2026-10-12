-- ============================================================
-- 结构与脚本漂移修复（P5-23 / P5-24 / P5-25 / P5-26）
-- 2026-10-09
--
-- 背景：线上库是「手工演进」出来的，与 sql/db_init.sql 的声明长期不一致。
--       这些漂移平时不报错，只在特定数据下才炸（金额过亿、写入 emoji、退资漏行），
--       属于典型的「定时炸弹型」缺陷。
--
-- 覆盖：
--   P5-23  16 张主表字符集退化为 utf8mb3_general_ci（脚本声明 utf8mb4）
--          ⇒ 写 4 字节字符（emoji / 生僻字）直接报 Incorrect string value → 接口 500
--   P5-24  金额列被压成 decimal(10,2)（上限 99,999,999.99）
--          ⇒ 金额 ≥ 1 亿时报 Out of range → 500
--   P5-25  lend.risk_level / lend.money_use 库中不存在，但脚本在建、Controller 在读
--          ⇒ 风险等级 / 资金用途被静默丢弃
--   P5-26  lend_item.status 可空（脚本声明 NOT NULL DEFAULT 0）
--          ⇒ 这是 P1-5 / P1-6 的共同根因：指望数据库兜底为 0 的写入会落成 NULL，
--            使退资查询 `status IN (0,2)` 整行漏掉（钱退不出去）
--
-- 不覆盖（另见）：
--   P5-27  5 处唯一约束只存在于库里、db_init.sql 未声明 —— 属脚本侧修复，
--          已在 db_init.sql 补 uk_user_id / uk_user_id / uk_user_id / uk_return_no /
--          uk_parent_id_value 五条（本脚本不动库，库中这 5 条已存在）。
--
-- 幂等：全部为「重复执行结果不变」的 DDL；ADD COLUMN 用 information_schema + PREPARE 守卫
--       （MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS）。
-- 回滚：DDL 不可逐条回滚，出事用全库备份还原
--       .workbuddy/tmp/backup_ww_finance_20261009_pre_ddl.sql
-- ============================================================

USE ww_finance;

-- ------------------------------------------------------------
-- P5-23：字符集 utf8mb3 → utf8mb4
-- 目标排序规则选 utf8mb4_general_ci：与 db_init.sql 的
--   CREATE DATABASE ... COLLATE utf8mb4_general_ci 以及各表 DEFAULT CHARSET=utf8mb4 一致。
--   （库中另有 18 张表是 utf8mb4_0900_ai_ci、2 张是 utf8mb4_general_ci，本次不动它们，
--     避免把「已正确」的表卷进来。）
-- ------------------------------------------------------------
ALTER TABLE `borrow_info`      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `borrower`         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `borrower_attach`  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `dict`             CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `integral_grade`   CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `investment_list`  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `lend`             CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `lend_item`        CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `lend_item_return` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `lend_return`      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `trans_flow`       CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `user`             CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `user_account`     CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `user_bind`        CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `user_integral`    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `user_login_record` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- ------------------------------------------------------------
-- P5-24：金额列 decimal(10,2) → decimal(20,2)
-- 定义与 db_init.sql 对齐（含 NULL / NOT NULL / DEFAULT）。
-- 利率列（lend_year_rate / service_rate / borrow_year_rate）不是金额，保持不动。
-- 前提已核实：lend.amount / lend.invest_amount / lend_item.invest_amount 无 NULL 行，
--             故可与脚本一致地收紧为 NOT NULL。
-- ------------------------------------------------------------
-- 用户账户（余额真源的镜像，最不能溢出）
ALTER TABLE `user_account`
  MODIFY COLUMN `amount`        decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '可用余额',
  MODIFY COLUMN `freeze_amount` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额';

-- 借款申请
ALTER TABLE `borrow_info`
  MODIFY COLUMN `amount` decimal(20,2) DEFAULT NULL COMMENT '借款金额';

-- 标的
ALTER TABLE `lend`
  MODIFY COLUMN `amount`         decimal(20,2) NOT NULL COMMENT '标的金额',
  MODIFY COLUMN `invest_amount`  decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '已募集金额',
  MODIFY COLUMN `lowest_amount`  decimal(20,2) DEFAULT '100.00' COMMENT '最低起投金额',
  MODIFY COLUMN `expect_amount`  decimal(20,2) DEFAULT NULL COMMENT '预期募集金额',
  MODIFY COLUMN `real_amount`    decimal(20,2) DEFAULT NULL COMMENT '实际募集金额';

-- 投资记录
ALTER TABLE `lend_item`
  MODIFY COLUMN `invest_amount` decimal(20,2) NOT NULL COMMENT '投资金额',
  MODIFY COLUMN `expect_amount` decimal(20,2) DEFAULT NULL COMMENT '预期收益',
  MODIFY COLUMN `real_amount`   decimal(20,2) DEFAULT NULL COMMENT '实际收益';

-- 还款计划（标的一期一行）
ALTER TABLE `lend_return`
  MODIFY COLUMN `amount`        decimal(20,2) DEFAULT NULL COMMENT '标的总金额',
  MODIFY COLUMN `base_amount`   decimal(20,2) DEFAULT NULL COMMENT '剩余本金',
  MODIFY COLUMN `principal`     decimal(20,2) DEFAULT NULL COMMENT '本期本金',
  MODIFY COLUMN `interest`      decimal(20,2) DEFAULT NULL COMMENT '本期利息',
  MODIFY COLUMN `total`         decimal(20,2) DEFAULT NULL COMMENT '本期应还总额',
  MODIFY COLUMN `fee`           decimal(20,2) DEFAULT NULL COMMENT '手续费',
  MODIFY COLUMN `overdue_total` decimal(20,2) DEFAULT NULL COMMENT '逾期金额（含罚息）';

-- 回款计划（投资人一期一行）
ALTER TABLE `lend_item_return`
  MODIFY COLUMN `invest_amount` decimal(20,2) DEFAULT NULL COMMENT '投资本金',
  MODIFY COLUMN `principal`     decimal(20,2) DEFAULT NULL COMMENT '本期应得本金',
  MODIFY COLUMN `interest`      decimal(20,2) DEFAULT NULL COMMENT '本期应得利息',
  MODIFY COLUMN `total`         decimal(20,2) DEFAULT NULL COMMENT '本期应得总额',
  MODIFY COLUMN `fee`           decimal(20,2) DEFAULT NULL COMMENT '手续费',
  MODIFY COLUMN `overdue_total` decimal(20,2) DEFAULT NULL COMMENT '逾期金额';

-- 资金流水
ALTER TABLE `trans_flow`
  MODIFY COLUMN `trans_amount` decimal(20,2) DEFAULT NULL COMMENT '交易金额';

-- 积分等级（可借额度也是金额列，同样不得被压在 decimal(10,2)）
ALTER TABLE `integral_grade`
  MODIFY COLUMN `borrow_amount` decimal(20,2) DEFAULT NULL COMMENT '可借额度';

-- ------------------------------------------------------------
-- P5-25：补 lend.risk_level / lend.money_use
-- 类型与 db_init.sql 一致：risk_level tinyint(4) DEFAULT '1'、money_use tinyint(4) DEFAULT NULL。
-- 补列后 AdminLendController 读到的 riskLevel / moneyUse 不再被静默丢弃。
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lend' AND COLUMN_NAME = 'money_use'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `lend` ADD COLUMN `money_use` tinyint(4) DEFAULT NULL COMMENT ''资金用途'' AFTER `lowest_amount`',
    'SELECT ''lend.money_use 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lend' AND COLUMN_NAME = 'risk_level'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `lend` ADD COLUMN `risk_level` tinyint(4) DEFAULT ''1'' COMMENT ''风险等级 1低 2中 3高'' AFTER `status`',
    'SELECT ''lend.risk_level 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- P5-26：lend_item.status 收紧为 NOT NULL DEFAULT 0
-- 先把存量 NULL 兜成 0（否则 ALTER 会把 NULL 静默变成 0 且不报错，不如显式写）
-- 这一步同时修复 P1-5 / P1-6：NULL 状态位会让退资查询 status IN (0,2) 漏行。
-- ------------------------------------------------------------
UPDATE `lend_item` SET `status` = 0 WHERE `status` IS NULL;

ALTER TABLE `lend_item`
  MODIFY COLUMN `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态 0已投标 1已撤标 2已满标 3已结算';

-- ------------------------------------------------------------
-- 校验
-- ------------------------------------------------------------
SELECT '--- P5-23 残留 utf8mb3 表（应为 0 行）---' AS chk;
SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_COLLATION NOT LIKE 'utf8mb4%';

SELECT '--- P5-24 残留 decimal(10,2) 金额列（应为 0 行）---' AS chk2;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_TYPE LIKE 'decimal(10,2)%'
  AND COLUMN_NAME NOT IN ('lend_year_rate', 'service_rate', 'borrow_year_rate');

SELECT '--- P5-25 / P5-26 终态 ---' AS chk3;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'lend' AND COLUMN_NAME IN ('risk_level', 'money_use'))
    OR (TABLE_NAME = 'lend_item' AND COLUMN_NAME = 'status'));
