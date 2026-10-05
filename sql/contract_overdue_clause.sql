-- ============================================================
-- 合同模板补充：满标后超期未签《放款确认书》的资金处置条款
-- ============================================================
-- 背景：
--   标的满标(COMPLETED)后投资人资金即被存管银行冻结。若借款人迟迟不签《放款确认书》，
--   资金既不能放款也不能退回，形成"无限期悬停"——这是监管口径下的资金池风险（合规红线）。
--
--   代码层面已提供处置出口（offline() 放开 COMPLETED + withdrawAllInvestments 覆盖 status=2），
--   但平台单方处置投资人的冻结资金，必须有**事先约定**作为法律依据，否则处置行为本身违规。
--   本脚本就是把这句约定写进《借款合同》与《出借协议》。
--
-- 说明：
--   1. 走模板版本化机制：新增 version+1 并启用，同编号旧版本 status=0 退役。
--      运行时按 status=1 ORDER BY version DESC LIMIT 1 取，保证同一编号只有 1 个启用版本。
--   2. 已签署的合同靠 content_snapshot 冻结，改模板不影响任何历史合同。
--   3. 幂等：重复执行不会产生重复版本（NOT EXISTS 守卫）。
--
-- 执行：mysql -uroot -p123456 ww_finance --default-character-set=utf8mb4 < 本文件
-- ============================================================

SET NAMES utf8mb4;

-- ---------- 1) 《出借协议》TPL_INVEST：新增版本 ----------
INSERT INTO contract_template (template_no, contract_type, title, content_tpl, version, status, is_deleted)
SELECT t.template_no,
       t.contract_type,
       t.title,
       CONCAT(t.content_tpl,
              '\n\n五、资金冻结与处置约定：甲方知悉并同意，出借资金自投标成功时起由存管银行冻结，'
              '平台不接触、不归集该资金。标的满标后，借款人应在 3 个自然日内签署《放款确认书》完成放款确认；'
              '逾期未完成的，平台有权终止本次借贷撮合，并通知存管银行解除对甲方出借资金的冻结，'
              '资金原路退回甲方账户，本协议自动终止，甲方无需承担任何费用。'),
       t.version + 1,
       1,
       0
FROM contract_template t
WHERE t.template_no = 'TPL_INVEST'
  AND t.status = 1
  AND NOT EXISTS (SELECT 1
                  FROM contract_template x
                  WHERE x.template_no = 'TPL_INVEST'
                    AND x.version = t.version + 1);

-- ---------- 2) 《借款合同》TPL_LOAN：新增版本 ----------
INSERT INTO contract_template (template_no, contract_type, title, content_tpl, version, status, is_deleted)
SELECT t.template_no,
       t.contract_type,
       t.title,
       CONCAT(t.content_tpl,
              '\n\n六、放款确认与逾期处理：乙方应在标的满标后 3 个自然日内签署《放款确认书》完成放款确认。'
              '逾期未签署的，甲方（出借人）有权终止本次借贷，平台将通知存管银行解除资金冻结并将款项退回出借人，'
              '本合同自动终止，乙方不得就已终止的标的主张放款。'),
       t.version + 1,
       1,
       0
FROM contract_template t
WHERE t.template_no = 'TPL_LOAN'
  AND t.status = 1
  AND NOT EXISTS (SELECT 1
                  FROM contract_template x
                  WHERE x.template_no = 'TPL_LOAN'
                    AND x.version = t.version + 1);

-- ---------- 3) 退役同编号的旧版本（保证只有一个启用版本） ----------
UPDATE contract_template c
  JOIN (SELECT template_no, MAX(version) AS max_version
        FROM contract_template
        WHERE template_no IN ('TPL_LOAN', 'TPL_INVEST')
        GROUP BY template_no) m
    ON c.template_no = m.template_no
SET c.status = 0
WHERE c.version < m.max_version;

-- ---------- 4) 校验：每个编号应恰好有 1 个 status=1，且是最大版本 ----------
SELECT template_no,
       COUNT(*)                                  AS total_versions,
       SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS enabled_versions,
       MAX(version)                              AS latest_version
FROM contract_template
WHERE template_no IN ('TPL_LOAN', 'TPL_INVEST', 'TPL_PAYMENT')
GROUP BY template_no;
