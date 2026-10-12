-- ============================================================
-- 修复：手机号唯一性（"一个账号/一个手机号还能重复注册"）
-- 背景：
--   `user`.`mobile` 长期只有一个**普通索引**（名字叫 uk_mobile，但 non_unique=1），
--   无论改名前还是现网都**没有数据库级唯一约束**。
--   应用层 `UserServiceImpl.register/addMember` 用
--   `getByMobile(mobile) != null` 做查重，但这是典型的 check-then-insert：
--   两个并发请求可以同时通过查重，双双写入 → 重复账号。
--   真实事故：手机号 15138019132 被写成 id=33 / id=34 两行（create_time 完全相同）。
--   另外 `getByMobile` 带 `LIMIT 1`，即使已有重复也只返回第一条，把问题掩盖了。
--
-- 本脚本做三件事：
--   1) 备份所有重复手机号的行
--   2) 列出"纯孤儿"重复行（在 14 张 user_id 关联表里都没有数据）供人工确认后删除
--   3) 幂等地把 uk_mobile 变成真正的 UNIQUE KEY
--
-- 执行：mysql -uroot -p123456 ww_finance --default-character-set=utf8mb4 < sql/fix_user_mobile_unique.sql
-- 说明：第 3 步的前置条件是"已无重复手机号"（否则 ALTER 会失败），请先执行第 2 步。
-- ============================================================

USE ww_finance;

-- ---------- 1) 备份重复行（可回滚） ----------
CREATE TABLE IF NOT EXISTS user_dup_backup_20261008 AS
SELECT u.* FROM `user` u
JOIN (SELECT mobile FROM `user` WHERE mobile IS NOT NULL GROUP BY mobile HAVING COUNT(*) > 1) d
  ON u.mobile = d.mobile;

-- ---------- 2) 列出重复手机号行 + 各自的关联数据量 ----------
-- 保留规则：保留有 user_account / user_bind / 登录记录的那一行；其余为纯孤儿，可物理删除。
SELECT u.id, u.mobile, u.name, u.user_type, u.is_deleted, u.create_time,
       (SELECT COUNT(*) FROM user_account         x WHERE x.user_id = u.id) AS account_cnt,
       (SELECT COUNT(*) FROM user_bind            x WHERE x.user_id = u.id) AS bind_cnt,
       (SELECT COUNT(*) FROM user_login_record    x WHERE x.user_id = u.id) AS login_cnt
FROM `user` u
WHERE u.mobile IN (SELECT mobile FROM `user` WHERE mobile IS NOT NULL GROUP BY mobile HAVING COUNT(*) > 1)
ORDER BY u.mobile, u.id;

-- 确认上述孤儿后，手工执行（把 <孤儿id> 换成上一步查出来的 id）：
-- DELETE FROM `user` WHERE id IN (<孤儿id>);

-- ---------- 3) 幂等：把 uk_mobile 换成真正的唯一索引 ----------
-- 3.1 若是"同名的非唯一索引"，先删掉
SET @has := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'user' AND index_name = 'uk_mobile');
SET @nonuniq := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'user' AND index_name = 'uk_mobile'
                   AND non_unique = 1 AND seq_in_index = 1);
SET @sql := IF(@has > 0 AND @nonuniq = 1,
               'ALTER TABLE `user` DROP INDEX `uk_mobile`',
               'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3.2 若还没有 uk_mobile，则加真正的唯一约束
SET @has2 := (SELECT COUNT(*) FROM information_schema.statistics
              WHERE table_schema = DATABASE() AND table_name = 'user' AND index_name = 'uk_mobile');
SET @sql2 := IF(@has2 = 0,
                'ALTER TABLE `user` ADD UNIQUE KEY `uk_mobile` (`mobile`)',
                'DO 0');
PREPARE s2 FROM @sql2; EXECUTE s2; DEALLOCATE PREPARE s2;

-- ---------- 4) 校验 ----------
-- 4.1 唯一性：应为空结果（无重复手机号）
SELECT mobile, COUNT(*) AS c, GROUP_CONCAT(id) AS ids
FROM `user` WHERE mobile IS NOT NULL GROUP BY mobile HAVING c > 1;
-- 4.2 索引：Non_unique 应为 0
SHOW INDEX FROM `user` WHERE Key_name = 'uk_mobile';
