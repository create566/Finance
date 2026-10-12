-- ============================================================
-- 补齐两类缺失的核心合同（P9-60 居间服务协议 / P9-61 资金存管协议）
-- ============================================================
-- 背景：
--   规格要求平台有三类核心合同，实际只实现了《借款合同》《出借协议》《放款确认书》，
--   下面两类在系统里完全不存在（全库无任何 %agree%/%service%/%deposit% 表或字段）：
--     ① 居间服务协议（平台 ↔ 借款人 / 平台 ↔ 投资人）—— 平台收取撮合服务费的合同依据；
--     ② 资金存管协议（用户 ↔ 存管银行）—— 用户开立存管账户、约定充提与资金保管规则。
--
--   本脚本只补模板；合同的生成/签署由代码负责：
--     · ContractTypeEnum.SERVICE / DEPOSIT
--     · ContractBizTypeEnum.USER（biz_id = userId，amount 固定 0）
--     · ContractServiceImpl.getOrCreateUserAgreement / assertUserAgreementSigned
--   触发时机：
--     · SERVICE —— 借款人建标时预生成、发布前必签；投资人首次投标前必签（用户级一次性）
--     · DEPOSIT —— 用户在「我的合同」页进入即自动生成，绑卡前必签
--
--   主体口径（与代码字段一致，避免重复 P9-58 的"正文与字段指向相反"）：
--     甲方 = 用户（party_a_id = userId）；乙方 = 平台 / 存管银行（只落 party_b_name，不落 id）。
--
-- 幂等：NOT EXISTS 守卫，重复执行不产生重复模板。
-- 执行：mysql -uroot -p123456 ww_finance --default-character-set=utf8mb4 < 本文件
-- ============================================================

SET NAMES utf8mb4;

-- ---------- 1) 居间服务协议（平台 ↔ 借款人 / 平台 ↔ 投资人） ----------
INSERT INTO `contract_template`
  (`template_no`, `contract_type`, `title`, `content_tpl`, `variables`, `version`, `status`, `is_deleted`)
SELECT
  'TPL_SERVICE',
  'SERVICE',
  '居间服务协议',
  '居间服务协议\n\n协议编号：${contractNo}\n\n甲方（用户）：${userName}\n身份证号：${userIdCard}\n乙方（居间服务方）：${platformName}\n\n一、服务内容：乙方为甲方提供借贷信息发布与撮合、借款人资信初步审核、电子合同辅助签署等信息中介服务。\n二、服务费用：就乙方撮合成交的借贷业务，乙方有权按标的公示或系统记录的服务费率收取撮合服务费，具体金额以系统记录为准。\n三、责任边界：乙方仅提供信息中介服务，不对借贷本金、利息、罚息承担任何代偿、担保、回购责任；借贷债权债务仅存在于出借人与借款人之间。\n四、资金安全：借贷资金由存管银行保管与划转，乙方不接触、不归集、不挪用客户资金。\n五、甲方确认已充分阅读并理解上述条款，自愿接受乙方提供的信息中介服务。\n本协议自甲方手写签署之日起生效，正文以系统快照为准，签署后不得单方修改。\n\n签署人（甲方）：____________    签署日期：${signDate}',
  'contractNo,userName,userIdCard,platformName,signDate',
  1,
  1,
  0
FROM (SELECT 1) dummy
WHERE NOT EXISTS (
  SELECT 1 FROM `contract_template` x WHERE x.template_no = 'TPL_SERVICE'
);

-- ---------- 2) 资金存管协议（用户 ↔ 存管银行） ----------
INSERT INTO `contract_template`
  (`template_no`, `contract_type`, `title`, `content_tpl`, `variables`, `version`, `status`, `is_deleted`)
SELECT
  'TPL_DEPOSIT',
  'DEPOSIT',
  '资金存管协议',
  '资金存管协议\n\n协议编号：${contractNo}\n\n甲方（用户）：${userName}\n身份证号：${userIdCard}\n乙方（存管方）：${bankName}\n\n一、乙方依据本协议为甲方开立资金存管账户，用于存放甲方通过旺旺金融平台产生的充值、提现、投资及回款资金。\n二、甲方知悉并同意：充值、提现、投资、还款等资金划转均由乙方依据甲方的支付指令执行，平台不接触客户资金。\n三、甲方应妥善保管支付密码及验证信息，因甲方保管不善造成的资金损失由甲方自行承担。\n四、甲方首次绑卡即视为已阅读并同意本协议全部条款；本协议自甲方手写签署之日起生效。\n五、本协议项下资金的保管、划转及争议处理，以乙方的业务规则为准。\n\n签署人（甲方）：____________    签署日期：${signDate}',
  'contractNo,userName,userIdCard,bankName,signDate',
  1,
  1,
  0
FROM (SELECT 1) dummy
WHERE NOT EXISTS (
  SELECT 1 FROM `contract_template` x WHERE x.template_no = 'TPL_DEPOSIT'
);

-- ---------- 3) 校验：5 个编号各应有且只有 1 个启用版本 ----------
SELECT template_no,
       contract_type,
       COUNT(*)                                   AS total_versions,
       SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS enabled_versions,
       MAX(version)                               AS latest_version
FROM contract_template
WHERE template_no IN ('TPL_LOAN', 'TPL_INVEST', 'TPL_PAYMENT', 'TPL_SERVICE', 'TPL_DEPOSIT')
GROUP BY template_no, contract_type
ORDER BY template_no;
