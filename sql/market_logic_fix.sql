-- ============================================================
-- 市场逻辑修正：资金流水状态 + 不可能的负余额（可重复执行）
--
-- 背景（两个真实缺陷）：
--   ① trans_flow 只有"成功时"才落库 —— 充值与提现在银行回调成功后才写流水，
--      用户点了充值/提现、银行还在处理时，【我的资金 → 资金流水】里什么都看不到。
--      市场通行做法是"发起即落一笔处理中流水，回调后置为成功/失败"，
--      这样用户随时能看到"我提现的那笔正在处理中"，对账也有据可循。
--   ② user_account 存在 amount 为负数的行（例：user_id=20 为 -5130.12）。
--      可用余额是负数在业务上不可能出现，属于历史脏数据。
--
-- 本脚本做三件事：
--   ① trans_flow 增加 status（0处理中 / 1成功 / 2失败）
--   ② 历史流水统一置为成功（它们都是回调落库的，见上面的成因）
--   ③ 修正 user_account 中的负数余额
--   ④ 校验结果
--
-- 用法：
--   mysql -uroot -p123456 -h127.0.0.1 ww_finance --default-character-set=utf8mb4 < sql/market_logic_fix.sql
-- ============================================================

-- ① trans_flow.status ------------------------------------------------------------
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trans_flow'
      AND COLUMN_NAME = 'status') = 0,
  'ALTER TABLE `trans_flow` ADD COLUMN `status` TINYINT(4) NOT NULL DEFAULT 1 COMMENT ''交易状态(0处理中 1成功 2失败)'' AFTER `trans_amount`',
  'SELECT ''trans_flow.status 已存在，跳过'' AS msg'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ② 历史流水视为成功 -------------------------------------------------------------
--    历史行都是在银行回调成功分支里插入的，语义上就是"成功"。
--    默认值已保证新列为 1，这里显式兜一次，防止某些库 ALTER 时补的是 NULL。
UPDATE `trans_flow` SET `status` = 1 WHERE `status` IS NULL;

-- ③ 修正负余额 -------------------------------------------------------------------
--    可用余额为负说明历史扣款越过了闸门。这里只把不可能的负值拉回 0，
--    不臆造任何"补偿入账"流水（那会污染对账）。
UPDATE `user_account` SET `amount` = 0 WHERE `amount` < 0;
UPDATE `user_account` SET `freeze_amount` = 0 WHERE `freeze_amount` < 0;

-- ④ 校验 -------------------------------------------------------------------------
SELECT 'trans_flow.status 分布' AS item,
       CONCAT('0处理中=', SUM(`status` = 0),
              '  1成功=', SUM(`status` = 1),
              '  2失败=', SUM(`status` = 2)) AS detail
  FROM `trans_flow`
UNION ALL
SELECT 'user_account 负余额行数',
       CAST(SUM(`amount` < 0 OR `freeze_amount` < 0) AS CHAR)
  FROM `user_account`;

-- ---------- 回滚（如需）----------
-- ALTER TABLE `trans_flow` DROP COLUMN `status`;
