-- ============================================================================
-- 资金闭环 · 恒等式⑧ 数据回填：「预期收益」统一为「实际可收利息合计」
--
-- 背景：改造前 `lend_item.expect_amount` 由用户端单利算法写入
--       （金额 × 年利率 × 期数/12），与还款计划里真实的利息合计差 1.82 倍。
--       代码侧已改走 ExpectReturnUtil（= Σ ReturnPlanCalculator 各期利息），
--       但**存量行仍是旧值**，必须回填，否则页面继续显示拿不到的钱。
--
-- 回填口径（优先级从高到低）：
--   A. 该笔投资已生成回款明细（标的已放款）→ 直接取 Σ lend_item_return.interest
--      —— 这就是该投资人**未来实际会收到的利息**，是唯一权威值。
--   B. 标的尚未放款、没有回款计划 → 用「同利率/同期限/同还款方式」的已放款标的
--      折算"每元本金的利息"再乘本笔投资额。放款生成计划后，B 会被 A 覆盖
--      （放款流程在生成回款明细后会重算 expect_amount）。
--
-- 复核：执行后重跑 sql/verify_fund_invariants.sql，恒等式⑧ 应无输出。
-- 用法：
--   "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p123456 \
--     --default-character-set=utf8mb4 ww_finance < sql/fix_expect_amount.sql
-- ============================================================================

-- 回填前先看一眼将被改动的行（对照用）
SELECT '回填前' AS phase,
       li.id AS item_id, li.lend_id, li.invest_user_id,
       li.invest_amount, li.expect_amount AS old_expect,
       (SELECT ROUND(SUM(x.interest), 2) FROM lend_item_return x
         WHERE x.lend_item_id = li.id AND x.is_deleted = 0) AS plan_interest
FROM lend_item li
WHERE li.is_deleted = 0
ORDER BY li.id;

-- ---------- 回填 ----------
UPDATE lend_item li
JOIN lend l ON l.id = li.lend_id
LEFT JOIN (
    -- 每个「利率+期限+还款方式」组合的每元本金利息（取已放款标的的平均值）
    SELECT l2.lend_year_rate                AS rate,
           l2.period                        AS period,
           l2.return_method                 AS method,
           AVG(s.interest_sum / l2.amount)  AS per_unit
    FROM lend l2
    JOIN (
        SELECT lend_id, SUM(interest) AS interest_sum
        FROM lend_return
        WHERE is_deleted = 0
        GROUP BY lend_id
    ) s ON s.lend_id = l2.id
    WHERE l2.is_deleted = 0 AND l2.amount > 0
    GROUP BY l2.lend_year_rate, l2.period, l2.return_method
) ref ON ref.rate = l.lend_year_rate
     AND ref.period = l.period
     AND ref.method <=> l.return_method
SET li.expect_amount = IFNULL(
        (SELECT ROUND(SUM(x.interest), 2) FROM lend_item_return x
          WHERE x.lend_item_id = li.id AND x.is_deleted = 0),
        ROUND(li.invest_amount * IFNULL(ref.per_unit, 0), 2))
WHERE li.is_deleted = 0
  AND IFNULL(li.expect_amount, -1) <> IFNULL(
        (SELECT ROUND(SUM(x.interest), 2) FROM lend_item_return x
          WHERE x.lend_item_id = li.id AND x.is_deleted = 0),
        ROUND(li.invest_amount * IFNULL(ref.per_unit, 0), 2));

-- 回填后核对
SELECT '回填后' AS phase,
       li.id AS item_id, li.lend_id, li.invest_user_id,
       li.invest_amount, li.expect_amount AS new_expect,
       (SELECT ROUND(SUM(x.interest), 2) FROM lend_item_return x
         WHERE x.lend_item_id = li.id AND x.is_deleted = 0) AS plan_interest
FROM lend_item li
WHERE li.is_deleted = 0
ORDER BY li.id;
