-- ============================================================
-- 旺旺金融 实训环境数据清理（软删）
-- 日期：2026-10-12
-- 范围：全部标的业务数据 + 全部合同 + 资金流水
-- 保留：user / user_account / user_bind / sys_admin / dict / contract_template
--       contract_sign_record（签署存证，法律证据，禁删）
--       lend_settle_record（结算记录，审计用）
-- 方式：全部 is_deleted = 1（软删），不做物理 DELETE
--
-- 依赖：执行前已做 mysqldump 备份
--   .workbuddy/tmp/backup/ww_finance_pre_clean_20261012_1557.sql
--
-- 注意：contract_sign_record / lend_settle_record / reconciliation_record
--       三张表**没有 is_deleted 列**，且属审计证据，本脚本不动它们。
-- ============================================================

-- ---------- 步骤 0：清理前计数（对照用） ----------
SELECT '=== 清理前 ===' AS step;
SELECT 'lend' t, COUNT(*) c FROM lend WHERE is_deleted=0
UNION ALL SELECT 'lend_item', COUNT(*) FROM lend_item WHERE is_deleted=0
UNION ALL SELECT 'lend_return', COUNT(*) FROM lend_return WHERE is_deleted=0
UNION ALL SELECT 'lend_item_return', COUNT(*) FROM lend_item_return WHERE is_deleted=0
UNION ALL SELECT 'trans_flow', COUNT(*) FROM trans_flow WHERE is_deleted=0
UNION ALL SELECT 'contract', COUNT(*) FROM contract WHERE is_deleted=0
UNION ALL SELECT 'borrow_info', COUNT(*) FROM borrow_info WHERE is_deleted=0
UNION ALL SELECT 'investor', COUNT(*) FROM investor WHERE is_deleted=0
UNION ALL SELECT 'borrower', COUNT(*) FROM borrower WHERE is_deleted=0;

-- ---------- 步骤 1：分户明细（最子级） ----------
UPDATE lend_item_return SET is_deleted = 1 WHERE is_deleted = 0;

-- ---------- 步骤 2：还款计划 ----------
UPDATE lend_return SET is_deleted = 1 WHERE is_deleted = 0;

-- ---------- 步骤 3：投资份额 ----------
UPDATE lend_item SET is_deleted = 1 WHERE is_deleted = 0;

-- ---------- 步骤 4：标的 ----------
UPDATE lend SET is_deleted = 1 WHERE is_deleted = 0;

-- ---------- 步骤 5：借款申请 ----------
UPDATE borrow_info SET is_deleted = 1 WHERE is_deleted = 0;

-- ---------- 步骤 6：全部合同（40 份：20 标的 + 20 用户）----------
UPDATE contract SET is_deleted = 1 WHERE is_deleted = 0;

-- ---------- 步骤 7：资金流水 ----------
UPDATE trans_flow SET is_deleted = 1 WHERE is_deleted = 0;

-- ---------- 步骤 8：投资人/借款人身份绑定（user 表保留） ----------
UPDATE investor SET is_deleted = 1 WHERE is_deleted = 0;
UPDATE borrower SET is_deleted = 1 WHERE is_deleted = 0;

-- ---------- 步骤 9：清理后验证（期望全为 0） ----------
SELECT '=== 清理后（期望全为 0）===' AS step;
SELECT 'lend' t, COUNT(*) c FROM lend WHERE is_deleted=0
UNION ALL SELECT 'lend_item', COUNT(*) FROM lend_item WHERE is_deleted=0
UNION ALL SELECT 'lend_return', COUNT(*) FROM lend_return WHERE is_deleted=0
UNION ALL SELECT 'lend_item_return', COUNT(*) FROM lend_item_return WHERE is_deleted=0
UNION ALL SELECT 'trans_flow', COUNT(*) FROM trans_flow WHERE is_deleted=0
UNION ALL SELECT 'contract', COUNT(*) FROM contract WHERE is_deleted=0
UNION ALL SELECT 'borrow_info', COUNT(*) FROM borrow_info WHERE is_deleted=0
UNION ALL SELECT 'investor', COUNT(*) FROM investor WHERE is_deleted=0
UNION ALL SELECT 'borrower', COUNT(*) FROM borrower WHERE is_deleted=0;

-- ---------- 步骤 10：保留项验证（不应为 0） ----------
SELECT '=== 保留项（不应为 0）===' AS step;
SELECT 'user' t, COUNT(*) c FROM user WHERE is_deleted=0
UNION ALL SELECT 'user_account', COUNT(*) FROM user_account WHERE is_deleted=0
UNION ALL SELECT 'user_bind', COUNT(*) FROM user_bind WHERE is_deleted=0
UNION ALL SELECT 'sys_admin', COUNT(*) FROM sys_admin WHERE is_deleted=0
UNION ALL SELECT 'contract_template', COUNT(*) FROM contract_template WHERE is_deleted=0
UNION ALL SELECT 'contract_sign_record(存证)', COUNT(*) FROM contract_sign_record;
