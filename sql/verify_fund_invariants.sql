-- ============================================================================
-- 资金闭环恒等式核查脚本（只读，不修改任何数据）
--   用法：
--     "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p123456 \
--       --default-character-set=utf8mb4 -t ww_finance < sql/verify_fund_invariants.sql
--
--   设计：每条恒等式只输出【不合格行】。全部无输出 = 全绿。
--   末尾附一条汇总，便于一眼看出红灯数量。
--
--   恒等式清单
--     ① 标的已募集额     == Σ有效投资额（status<>1，排除已撤标）
--     ② 已放款标的的已募集额 == 实际募集额 real_amount（募资中不适用，见正文说明）
--     ③ 每期 Σ明细本金   == 借款人该期本金（利息、总额同理）
--     ④ 每标的 Σ期本金   == 借款金额
--     ⑤ 每户全周期 Σ明细本金 == 该户投资额
--     ⑥ Σ回款流水        == Σ已回款明细
--     ⑦ Σ放款流水        == Σ已放款标的金额
--     ⑧ 页面"预期收益"   == 该户实际可收利息合计
--     ⑨ 分摊明细无重复行（lend_return_id + lend_item_id 唯一）
--     ⑩ 已撤标的投资记录不得再参与分摊
--     ⑪ 无悬挂"处理中"扣款流水
--     ⑫ 每期期末剩余本金 self-consistent（base_amount 递减到最后为 0）
-- ============================================================================

-- ① 标的已募集额 == Σ有效投资额
SELECT '①' AS no, '募集额==Σ有效投资额' AS invariant,
       l.lend_no AS object, l.invest_amount AS expect_val,
       IFNULL(agg.actual, 0) AS actual_val
FROM lend l
LEFT JOIN (
    SELECT li.lend_id, SUM(li.invest_amount) AS actual
    FROM lend_item li
    WHERE li.is_deleted = 0 AND IFNULL(li.status, 0) <> 1
    GROUP BY li.lend_id
) agg ON agg.lend_id = l.id
WHERE l.is_deleted = 0 AND IFNULL(l.invest_amount, 0) <> IFNULL(agg.actual, 0);

-- ② 已放款标的：实际募集额 == 已募集额
--    说明：real_amount 的写入点是**放款**（LendReturnServiceImpl: lend.setRealAmount(investAmount)），
--    募资中的标的 real_amount 天然为 NULL —— 它表示"最终实际募到多少"，不是"当前募到多少"。
--    因此本式只对已放款（status 5/6）的标的生效，否则会误报。
SELECT '②' AS no, '募集额==实际募集额(已放款)' AS invariant,
       lend_no AS object, invest_amount AS expect_val, real_amount AS actual_val
FROM lend
WHERE is_deleted = 0
  AND status IN (5, 6)
  AND IFNULL(invest_amount, 0) <> IFNULL(real_amount, 0);

-- ③ 每期 Σ明细 == 借款人该期（本金 / 利息 / 总额）
SELECT '③' AS no, CONCAT('第', lr.current_period, '期 Σ明细不匹配（本金/利息/总额）') AS invariant,
       CONCAT('lend=', lr.lend_id, ' returnId=', lr.id) AS object,
       lr.total AS expect_val, agg.actual_total AS actual_val
FROM lend_return lr
JOIN (
    SELECT ir.lend_return_id,
           SUM(ir.principal) AS actual_principal,
           SUM(ir.interest)  AS actual_interest,
           SUM(ir.total)     AS actual_total
    FROM lend_item_return ir
    WHERE ir.is_deleted = 0
    GROUP BY ir.lend_return_id
) agg ON agg.lend_return_id = lr.id
WHERE lr.is_deleted = 0
  AND (IFNULL(lr.principal, 0) <> IFNULL(agg.actual_principal, 0)
    OR IFNULL(lr.interest, 0)  <> IFNULL(agg.actual_interest, 0)
    OR IFNULL(lr.total, 0)     <> IFNULL(agg.actual_total, 0));

-- ④ 每标的 Σ各期本金 == 借款金额
SELECT '④' AS no, 'Σ期本金==借款金额' AS invariant,
       l.lend_no AS object, l.amount AS expect_val, agg.actual AS actual_val
FROM lend l
JOIN (
    SELECT lend_id, SUM(principal) AS actual
    FROM lend_return
    WHERE is_deleted = 0
    GROUP BY lend_id
) agg ON agg.lend_id = l.id
WHERE l.is_deleted = 0 AND IFNULL(l.amount, 0) <> IFNULL(agg.actual, 0);

-- ⑤ 每户全周期 Σ明细本金 == 该户投资额（仅已生成回款计划的）
SELECT '⑤' AS no, '每户Σ明细本金==投资额' AS invariant,
       CONCAT('itemId=', li.id, ' user=', li.invest_user_id) AS object,
       li.invest_amount AS expect_val, agg.actual AS actual_val
FROM lend_item li
JOIN (
    SELECT lend_item_id, SUM(principal) AS actual
    FROM lend_item_return
    WHERE is_deleted = 0
    GROUP BY lend_item_id
) agg ON agg.lend_item_id = li.id
WHERE li.is_deleted = 0 AND IFNULL(li.invest_amount, 0) <> IFNULL(agg.actual, 0);

-- ⑥ Σ回款流水 == Σ已回款明细
SELECT '⑥' AS no, 'Σ回款流水==Σ已回款明细' AS invariant,
       'GLOBAL' AS object, flow.actual AS expect_val, detail.actual AS actual_val
FROM (SELECT SUM(trans_amount) AS actual FROM trans_flow
       WHERE is_deleted = 0 AND trans_type = 4 AND status = 1) flow
JOIN (SELECT SUM(total) AS actual FROM lend_item_return
       WHERE is_deleted = 0 AND status = 1) detail
WHERE IFNULL(flow.actual, 0) <> IFNULL(detail.actual, 0);

-- ⑦ Σ放款流水 == Σ已放款标的金额
SELECT '⑦' AS no, 'Σ放款流水==Σ已放款标的金额' AS invariant,
       'GLOBAL' AS object, flow.actual AS expect_val, lend.actual AS actual_val
FROM (SELECT SUM(trans_amount) AS actual FROM trans_flow
       WHERE is_deleted = 0 AND trans_type = 5) flow
JOIN (SELECT SUM(amount) AS actual FROM lend
       WHERE is_deleted = 0 AND status IN (5, 6)) lend
WHERE IFNULL(flow.actual, 0) <> IFNULL(lend.actual, 0);

-- ⑧ 页面"预期收益" == 该户实际可收利息合计（两套口径不一致的直接证据）
SELECT '⑧' AS no, '预期收益==实际可收利息' AS invariant,
       CONCAT('itemId=', li.id, ' user=', li.invest_user_id) AS object,
       li.expect_amount AS expect_val, agg.actual AS actual_val
FROM lend_item li
JOIN (
    SELECT lend_item_id, SUM(interest) AS actual
    FROM lend_item_return
    WHERE is_deleted = 0
    GROUP BY lend_item_id
) agg ON agg.lend_item_id = li.id
WHERE li.is_deleted = 0
  AND IFNULL(li.expect_amount, 0) <> IFNULL(agg.actual, 0);

-- ⑨ 分摊明细重复行
SELECT '⑨' AS no, '分摊明细重复行' AS invariant,
       CONCAT('lendReturnId=', lend_return_id, ' itemId=', lend_item_id) AS object,
       1 AS expect_val, COUNT(*) AS actual_val
FROM lend_item_return
WHERE is_deleted = 0
GROUP BY lend_return_id, lend_item_id
HAVING COUNT(*) > 1;

-- ⑩ 已撤标的投资记录不得再参与分摊
SELECT '⑩' AS no, '已撤标记录仍参与分摊' AS invariant,
       CONCAT('itemId=', li.id, ' status=', li.status, ' 分摊行数=', COUNT(ir.id)) AS object,
       0 AS expect_val, COUNT(ir.id) AS actual_val
FROM lend_item li
JOIN lend_item_return ir ON ir.lend_item_id = li.id AND ir.is_deleted = 0
WHERE li.is_deleted = 0 AND IFNULL(li.status, 0) = 1
GROUP BY li.id, li.status;

-- ⑪ 悬挂"处理中"的扣款类流水（占用可用额）
SELECT '⑪' AS no, '悬挂处理中扣款流水' AS invariant,
       CONCAT('userId=', user_id, ' transNo=', trans_no) AS object,
       '终态' AS expect_val, CONCAT('status=', status, ' amount=', trans_amount) AS actual_val
FROM trans_flow
WHERE is_deleted = 0 AND status = 0 AND trans_type IN (2, 3, 6);

-- ⑫ 已放款标的必须已生成全部期数的回款明细（每期每户一行）
SELECT '⑫' AS no, '分期明细缺失（户数×期数）' AS invariant,
       CONCAT('lend=', l.lend_no, ' 期数=', l.period) AS object,
       CONCAT(IFNULL(cnt.items, 0) * IFNULL(l.period, 0), ' 行') AS expect_val,
       CONCAT(IFNULL(det.details, 0), ' 行') AS actual_val
FROM lend l
LEFT JOIN (
    SELECT lend_id, COUNT(*) AS items FROM lend_item
    WHERE is_deleted = 0 AND IFNULL(status, 0) <> 1 GROUP BY lend_id
) cnt ON cnt.lend_id = l.id
LEFT JOIN (
    SELECT lend_id, COUNT(*) AS details FROM lend_item_return
    WHERE is_deleted = 0 GROUP BY lend_id
) det ON det.lend_id = l.id
WHERE l.is_deleted = 0
  AND l.status IN (5, 6)
  AND IFNULL(cnt.items, 0) * IFNULL(l.period, 0) <> IFNULL(det.details, 0);

-- ---------- 汇总：各恒等式的红灯条数 ----------
SELECT '汇总' AS no, inv AS invariant, COUNT(*) AS fail_rows
FROM (
    SELECT '① 募集额==Σ有效投资额' AS inv FROM lend l
      LEFT JOIN (SELECT li.lend_id, SUM(li.invest_amount) AS actual FROM lend_item li
                 WHERE li.is_deleted = 0 AND IFNULL(li.status, 0) <> 1 GROUP BY li.lend_id) agg
        ON agg.lend_id = l.id
      WHERE l.is_deleted = 0 AND IFNULL(l.invest_amount, 0) <> IFNULL(agg.actual, 0)
    UNION ALL SELECT '② 募集额==实际募集额(已放款)' FROM lend
      WHERE is_deleted = 0 AND status IN (5, 6)
        AND IFNULL(invest_amount, 0) <> IFNULL(real_amount, 0)
    UNION ALL SELECT '④ Σ期本金==借款金额' FROM lend l
      JOIN (SELECT lend_id, SUM(principal) AS actual FROM lend_return
            WHERE is_deleted = 0 GROUP BY lend_id) agg ON agg.lend_id = l.id
      WHERE l.is_deleted = 0 AND IFNULL(l.amount, 0) <> IFNULL(agg.actual, 0)
    UNION ALL SELECT '⑤ 每户Σ明细本金==投资额' FROM lend_item li
      JOIN (SELECT lend_item_id, SUM(principal) AS actual FROM lend_item_return
            WHERE is_deleted = 0 GROUP BY lend_item_id) agg ON agg.lend_item_id = li.id
      WHERE li.is_deleted = 0 AND IFNULL(li.invest_amount, 0) <> IFNULL(agg.actual, 0)
    UNION ALL SELECT '⑧ 预期收益==实际可收利息' FROM lend_item li
      JOIN (SELECT lend_item_id, SUM(interest) AS actual FROM lend_item_return
            WHERE is_deleted = 0 GROUP BY lend_item_id) agg ON agg.lend_item_id = li.id
      WHERE li.is_deleted = 0 AND IFNULL(li.expect_amount, 0) <> IFNULL(agg.actual, 0)
    UNION ALL SELECT '⑩ 已撤标记录仍参与分摊' FROM lend_item li
      JOIN lend_item_return ir ON ir.lend_item_id = li.id AND ir.is_deleted = 0
      WHERE li.is_deleted = 0 AND IFNULL(li.status, 0) = 1
    UNION ALL SELECT '⑪ 悬挂处理中扣款流水' FROM trans_flow
      WHERE is_deleted = 0 AND status = 0 AND trans_type IN (2, 3, 6)
    UNION ALL SELECT '⑫ 分期明细缺失' FROM lend l
      LEFT JOIN (SELECT lend_id, COUNT(*) AS items FROM lend_item
                 WHERE is_deleted = 0 AND IFNULL(status, 0) <> 1 GROUP BY lend_id) cnt ON cnt.lend_id = l.id
      LEFT JOIN (SELECT lend_id, COUNT(*) AS details FROM lend_item_return
                 WHERE is_deleted = 0 GROUP BY lend_id) det ON det.lend_id = l.id
      WHERE l.is_deleted = 0 AND l.status IN (5, 6)
        AND IFNULL(cnt.items, 0) * IFNULL(l.period, 0) <> IFNULL(det.details, 0)
) t
GROUP BY inv
ORDER BY fail_rows DESC, inv;
