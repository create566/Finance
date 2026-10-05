-- ============================================================
-- 标的推荐模块 —— 演示数据（可选，可重复执行）
-- ============================================================
-- 目的：现有 ww_finance 里只有 2 个「募资中」标的（id=8、10），
--       其中 id=10 已被 user 42 投过，导致 /auth/recommend 只剩 1 条结果，
--       且 lend_item 共现数据太少 → Item-CF 相似度恒为 0，无法展示融合效果。
--
-- 本脚本补 6 个不同利率/期限的募资中标 + 21 条投资记录，
-- 使「规则打分 + Item-CF 融合 + 多样性 + 推荐理由」都有可观察的产出。
--
-- 数据范围：lend.id 101~106，lend_item.id 101~121（与真实数据不冲突）
-- 回滚方式：见文件末尾 ROLLBACK 段
-- ============================================================

START TRANSACTION;

-- ---------- 0. 幂等：先清掉本脚本上次写入的数据 ----------
DELETE FROM `lend_item` WHERE `id` BETWEEN 101 AND 121;
DELETE FROM `lend` WHERE `id` BETWEEN 101 AND 106;

-- ---------- 1. 募资中的标的（status=1，且未满标） ----------
INSERT INTO `lend`
  (`id`, `user_id`, `lend_no`, `title`, `amount`, `period`, `lend_year_rate`, `service_rate`,
   `return_method`, `lowest_amount`, `invest_amount`, `invest_num`, `publish_date`,
   `lend_end_date`, `lend_info`, `status`, `is_deleted`)
VALUES
  (101, 33, 'LEND20260930DEMO01', '装修分期标-复投优选',   50000.00, 12,  9.50, 1.00, 1, 100.00, 20000.00, 3,
        DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(CURDATE(), INTERVAL 6 DAY), '个人住房装修分期，等额本息', 1, 0),
  (102, 13, 'LEND20260930DEMO02', '消费分期标-稳健理财',   30000.00,  6,  7.50, 1.00, 1, 100.00,  9000.00, 3,
        DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 5 DAY), '日常消费分期，期限短、风险低', 1, 0),
  (103, 15, 'LEND20260930DEMO03', '经营周转标-高收益',     20000.00, 18, 11.50, 1.00, 2, 100.00,  2000.00, 1,
        DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 6 DAY), '小微企业短期经营周转', 1, 0),
  (104, 17, 'LEND20260930DEMO04', '设备采购标-高收益优选', 80000.00, 24, 12.50, 1.00, 1, 500.00, 40000.00, 5,
        DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_ADD(CURDATE(), INTERVAL 7 DAY), '制造业设备采购，利率较高', 1, 0),
  (105, 19, 'LEND20260930DEMO05', '短期周转标-灵活低息',   15000.00,  3,  6.50, 1.00, 1, 100.00,  1000.00, 1,
        DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 4 DAY), '三个月短期周转，流动性好', 1, 0),
  (106, 21, 'LEND20260930DEMO06', '供应链标-即将满标',     60000.00, 12,  9.00, 1.00, 1, 100.00, 55000.00, 8,
        DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(CURDATE(), INTERVAL 2 DAY), '核心企业供应链应收账款', 1, 0);

-- ---------- 2. 投资记录 ----------
-- 设计意图：让 user 42 的已投集合 = {7, 10, 102, 106}，
--   其中 102 的投资人集合 = {42,4,6}、106 的投资人集合 = {42,3,4,5,6,7,9,11}、101 的 = {4,6,8}。
--   于是 CF 会给 101 打出高相似度（与 102、106 都有共现），
--   而 101 的借款人正是 33 —— user 42 投过的 lend 7 / 10 的借款人也是 33，
--   因此 101 会同时拿到「复投」理由与「协同推荐」标签，融合效果一眼可见。
INSERT INTO `lend_item`
  (`id`, `lend_item_no`, `lend_id`, `invest_user_id`, `invest_name`, `invest_amount`,
   `lend_year_rate`, `invest_time`, `status`, `is_deleted`)
VALUES
  -- 102：3 人共 9000
  (101, 'LI20260930DEMO001', 102, 42, '张伟',    3000.00,  7.50, DATE_SUB(NOW(), INTERVAL 2 DAY), 0, 0),
  (102, 'LI20260930DEMO002', 102,  4, '1984496756', 3000.00, 7.50, DATE_SUB(NOW(), INTERVAL 2 DAY), 0, 0),
  (103, 'LI20260930DEMO003', 102,  6, '1984573308', 3000.00, 7.50, DATE_SUB(NOW(), INTERVAL 2 DAY), 0, 0),
  -- 101：3 人共 20000
  (104, 'LI20260930DEMO004', 101,  4, '1984496756', 5000.00, 9.50, DATE_SUB(NOW(), INTERVAL 1 DAY), 0, 0),
  (105, 'LI20260930DEMO005', 101,  6, '1984573308', 5000.00, 9.50, DATE_SUB(NOW(), INTERVAL 1 DAY), 0, 0),
  (106, 'LI20260930DEMO006', 101,  8, '1985071064', 10000.00, 9.50, DATE_SUB(NOW(), INTERVAL 1 DAY), 0, 0),
  -- 103：1 人 2000
  (107, 'LI20260930DEMO007', 103, 43, '13800001234', 2000.00, 11.50, DATE_SUB(NOW(), INTERVAL 1 DAY), 0, 0),
  -- 104：5 人共 40000
  (108, 'LI20260930DEMO008', 104,  3, '张三', 8000.00, 12.50, DATE_SUB(NOW(), INTERVAL 3 DAY), 0, 0),
  (109, 'LI20260930DEMO009', 104,  5, 'CRUD测试用户-已修改', 8000.00, 12.50, DATE_SUB(NOW(), INTERVAL 3 DAY), 0, 0),
  (110, 'LI20260930DEMO010', 104,  7, '1984935513', 8000.00, 12.50, DATE_SUB(NOW(), INTERVAL 3 DAY), 0, 0),
  (111, 'LI20260930DEMO011', 104,  9, '19821347735', 8000.00, 12.50, DATE_SUB(NOW(), INTERVAL 3 DAY), 0, 0),
  (112, 'LI20260930DEMO012', 104, 11, '1378423769', 8000.00, 12.50, DATE_SUB(NOW(), INTERVAL 3 DAY), 0, 0),
  -- 105：1 人 1000
  (113, 'LI20260930DEMO013', 105, 43, '13800001234', 1000.00, 6.50, DATE_SUB(NOW(), INTERVAL 1 DAY), 0, 0),
  -- 106：8 人共 55000（即将满标）
  (114, 'LI20260930DEMO014', 106, 42, '张伟', 1000.00, 9.00, DATE_SUB(NOW(), INTERVAL 4 DAY), 0, 0),
  (115, 'LI20260930DEMO015', 106,  3, '张三', 1000.00, 9.00, DATE_SUB(NOW(), INTERVAL 4 DAY), 0, 0),
  (116, 'LI20260930DEMO016', 106,  4, '1984496756', 1000.00, 9.00, DATE_SUB(NOW(), INTERVAL 4 DAY), 0, 0),
  (117, 'LI20260930DEMO017', 106,  5, 'CRUD测试用户-已修改', 1000.00, 9.00, DATE_SUB(NOW(), INTERVAL 4 DAY), 0, 0),
  (118, 'LI20260930DEMO018', 106,  6, '1984573308', 1000.00, 9.00, DATE_SUB(NOW(), INTERVAL 4 DAY), 0, 0),
  (119, 'LI20260930DEMO019', 106,  7, '1984935513', 20000.00, 9.00, DATE_SUB(NOW(), INTERVAL 4 DAY), 0, 0),
  (120, 'LI20260930DEMO020', 106,  9, '19821347735', 14000.00, 9.00, DATE_SUB(NOW(), INTERVAL 4 DAY), 0, 0),
  (121, 'LI20260930DEMO021', 106, 11, '1378423769', 16000.00, 9.00, DATE_SUB(NOW(), INTERVAL 4 DAY), 0, 0);

COMMIT;

-- 让推荐缓存整体失效（标的池已变化）
-- 若 Redis 里的 version key 存在，自增即可；不存在时由应用首次写缓存时补建。

-- ---------- 校验 ----------
-- SELECT id, user_id, title, amount, invest_amount, invest_num, period, lend_year_rate, status
--   FROM lend WHERE id BETWEEN 101 AND 106 ORDER BY id;
-- SELECT lend_id, COUNT(*) AS 投资人数, SUM(invest_amount) AS 合计
--   FROM lend_item WHERE lend_id BETWEEN 101 AND 106 GROUP BY lend_id ORDER BY lend_id;

-- ---------- 回滚（需要时单独执行） ----------
-- START TRANSACTION;
-- DELETE FROM `lend_item` WHERE `id` BETWEEN 101 AND 121;
-- DELETE FROM `lend` WHERE `id` BETWEEN 101 AND 106;
-- COMMIT;
