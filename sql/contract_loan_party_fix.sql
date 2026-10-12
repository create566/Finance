-- ============================================================
-- 《借款合同》主体口径修正（P9-58）+ 逾期罚息条款细化（P9-64 部分）
-- ============================================================
-- 背景（实测证据）：
--   原 TPL_LOAN 正文写"甲方（出借人）：旺旺金融平台投资人 / 乙方（借款人）：${borrowerName}"，
--   而 contract 表里 party_a_id = 借款人
--   （ContractServiceImpl.fillParties 的 else 分支只设甲方=借款人，party_b_id 留 NULL）。
--   ⇒ 同一份合同，正文与字段把"甲方"指给了相反的人；
--      且正文只有"签署人（乙方）"一个签名位，与"甲方=借款人单方签署"的事实也不一致。
--
--   本次把正文口径统一到代码事实，并补齐：
--     ① 甲方=借款人、乙方=全体出借人（明细以各《出借协议》为准）；
--     ② 甲方签名位 + 标的编号；
--     ③ 逾期罚息规则（与 LendReturnServiceImpl 实现完全一致：
--        罚息日利率 = 年化利率 ÷ 365 × 1.5；罚息上限为逾期本金的 100%；
--        对应常量 PENALTY_MULTIPLIER=1.5、penaltyCap=principal）。
--
-- 机制：走模板版本化（新增 version+1 并启用，同编号旧版本 status=0 退役）。
--       已签合同靠 content_snapshot 冻结，改模板不影响任何历史合同。
--       幂等：NOT EXISTS 守卫，重复执行不会产生重复版本。
--
-- 执行：mysql -uroot -p123456 ww_finance --default-character-set=utf8mb4 < 本文件
-- ============================================================

SET NAMES utf8mb4;

-- ---------- 1) 写入修正后的新版本 ----------
INSERT INTO `contract_template`
  (`template_no`, `contract_type`, `title`, `content_tpl`, `variables`, `version`, `status`, `is_deleted`)
SELECT
  'TPL_LOAN',
  'LOAN',
  '借款合同',
  '借款合同\n\n合同编号：${contractNo}\n标的编号：${lendNo}\n\n甲方（借款人）：${borrowerName}\n身份证号：${borrowerIdCard}\n乙方（出借人）：旺旺金融平台注册出借人（本合同的乙方为全体出借人；各出借人的姓名、出借金额及期次回款安排以对应的《出借协议》为准）\n居间方：旺旺金融平台（仅提供借贷信息撮合服务，不承担代偿、担保责任）\n\n一、借款金额：人民币 ${amount} 元（大写以系统记录为准）\n二、借款期限：${period} 期\n三、年化利率：${lendYearRate}%\n四、还款方式：按《借款申请》约定的还款方式执行；逐期还款计划以系统生成的《还款计划表》为准。\n五、借款用途：${purpose}\n六、逾期罚息：甲方未按期足额还款的，自逾期之日起按"罚息日利率 = 年化利率 ÷ 365 × 1.5"计收罚息，罚息总额不超过逾期本金的 100%，逾期记录按平台规则上报征信。\n\n甲方承诺：所提供资料真实有效，按期还本付息；本合同的债权债务仅存在于甲方与乙方之间，居间方不承担任何代偿、担保责任。\n本合同自甲方手写签署之日起生效，正文以系统快照为准，签署后不得单方修改。\n\n签署人（甲方）：____________    签署日期：${signDate}\n\n七、放款确认与逾期处理：甲方应在标的满标后 3 个自然日内签署《放款确认书》完成放款确认。逾期未签署的，乙方（出借人）有权终止本次借贷，平台将通知存管银行解除资金冻结并将款项退回出借人，本合同自动终止，甲方不得就已终止的标的主张放款。',
  'contractNo,lendNo,borrowerName,borrowerIdCard,amount,period,lendYearRate,purpose,signDate',
  (SELECT COALESCE(MAX(x.version), 0) + 1 FROM (SELECT version FROM `contract_template` WHERE template_no = 'TPL_LOAN') x),
  1,
  0
FROM (SELECT 1) dummy
WHERE NOT EXISTS (
  SELECT 1 FROM `contract_template` c
  WHERE c.template_no = 'TPL_LOAN' AND c.title = '借款合同' AND c.content_tpl LIKE '%甲方（借款人）：%'
);

-- ---------- 2) 退役同编号的旧版本（保证每个编号只剩 1 个启用版本） ----------
UPDATE `contract_template` c
  JOIN (SELECT template_no, MAX(version) AS max_version
        FROM `contract_template`
        WHERE template_no = 'TPL_LOAN'
        GROUP BY template_no) m
    ON c.template_no = m.template_no
SET c.status = 0
WHERE c.version < m.max_version;

-- ---------- 3) 校验 ----------
SELECT template_no,
       COUNT(*)                                    AS total_versions,
       SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END)  AS enabled_versions,
       MAX(version)                                AS latest_version
FROM contract_template
WHERE template_no = 'TPL_LOAN'
GROUP BY template_no;

-- 打印启用版本的正文，便于人工确认甲乙方口径已修正
SELECT content_tpl FROM contract_template WHERE template_no = 'TPL_LOAN' AND status = 1;
