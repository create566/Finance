-- ============================================================
-- P2-11 后台权限分级：sys_admin.role
-- 2026-10-09
--
-- 背景：此前 sys_admin 无角色字段，TokenUtil 把 token_role 硬编码成 "admin"，
--       结果是任何后台账号都能执行放款 / 下架 / 发标 / 作废合同 / 用户启停 —— 无最小权限约束。
--
-- 本次改动：
--   1. 新增 role 列（SUPER_ADMIN / ADMIN），默认 ADMIN；
--   2. 存量管理员统一升为 SUPER_ADMIN —— 否则现有环境的 admin 会立刻失去放款/下架权限，
--      实训演示直接不可用。新环境只有 admin 一个账号，同样置 SUPER_ADMIN。
--   3. 后续新建的后台账号默认 ADMIN，高危动作需显式提权。
--
-- 幂等：全部用 IF NOT EXISTS / 条件更新，可重复执行。
-- 回滚：ALTER TABLE sys_admin DROP COLUMN role;
-- ============================================================

USE ww_finance;

-- 1. 加列（MySQL 8.0 支持 ADD COLUMN IF NOT EXISTS 的替代写法：先判存在再加）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_admin' AND COLUMN_NAME = 'role'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `sys_admin` ADD COLUMN `role` VARCHAR(32) NOT NULL DEFAULT ''ADMIN'' COMMENT ''后台角色:SUPER_ADMIN/ADMIN'' AFTER `avatar`',
    'SELECT ''sys_admin.role 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 存量管理员升为超级管理员（只升不降，可重复执行）
--    用 username 而非 id 作为条件，避免不同环境 id 不一致。
UPDATE `sys_admin` SET `role` = 'SUPER_ADMIN'
WHERE `role` IS NULL OR `role` = 'ADMIN' OR `role` = '';

-- 3. 校验
SELECT `id`, `username`, `name`, `role`, `is_deleted` FROM `sys_admin` ORDER BY `id`;
