-- ============================================================
-- 电子合同签署模块 DDL（可重复执行）
-- 用法：mysql -uroot -p123456 -h127.0.0.1 ww_finance --default-character-set=utf8mb4 < sql/contract.sql
-- 覆盖：合同模板 / 合同实例 / 签署存证 三张表 + 两条内置模板
-- ============================================================

-- ① 合同模板（正文用 ${变量名} 占位，后台可维护）
CREATE TABLE IF NOT EXISTS `contract_template` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `template_no`    VARCHAR(64)  NOT NULL                COMMENT '模板编号',
  `contract_type`  VARCHAR(32)  NOT NULL                COMMENT 'LOAN借款合同 / INVEST出借协议 / PAYMENT放款确认书',
  `title`          VARCHAR(128) NOT NULL                COMMENT '合同标题',
  `content_tpl`    MEDIUMTEXT   NOT NULL                COMMENT '正文模板，变量用 ${变量名}',
  `variables`      VARCHAR(512)          DEFAULT NULL   COMMENT '变量清单(逗号分隔)',
  `version`        INT          NOT NULL DEFAULT 1      COMMENT '版本号',
  `status`         TINYINT      NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_no_ver` (`template_no`, `version`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同模板';

-- ② 合同实例（一份合同一条；uk_biz 保证同一业务的同类合同不会重复生成）
CREATE TABLE IF NOT EXISTS `contract` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `contract_no`      VARCHAR(64)  NOT NULL                COMMENT '合同编号',
  `contract_type`    VARCHAR(32)  NOT NULL                COMMENT 'LOAN / INVEST / PAYMENT',
  `biz_type`         VARCHAR(32)  NOT NULL                COMMENT 'LEND / BORROW_INFO / LEND_ITEM',
  `biz_id`           BIGINT       NOT NULL                COMMENT '业务主键',
  `template_id`      BIGINT       NOT NULL,
  `template_version` INT          NOT NULL                COMMENT '签署时的模板版本（追溯用）',
  `party_a_id`       BIGINT       NOT NULL                COMMENT '甲方 userId',
  `party_a_name`     VARCHAR(64)  NOT NULL,
  `party_b_id`       BIGINT                DEFAULT NULL   COMMENT '乙方 userId（单方签署可为空）',
  `party_b_name`     VARCHAR(64)           DEFAULT NULL,
  `amount`           DECIMAL(16,2)         DEFAULT NULL   COMMENT '合同金额',
  `lend_year_rate`   DECIMAL(6,2)          DEFAULT NULL   COMMENT '年化利率%',
  `period`           INT                   DEFAULT NULL   COMMENT '期数',
  `start_date`       DATE                  DEFAULT NULL,
  `end_date`         DATE                  DEFAULT NULL,
  `content_snapshot` MEDIUMTEXT   NOT NULL                COMMENT '变量渲染后的正文快照，签署后冻结',
  `content_hash`     CHAR(64)     NOT NULL                COMMENT '正文 SHA-256，防事后篡改',
  `status`           TINYINT      NOT NULL DEFAULT 0      COMMENT '0待签署 1已签署 2已作废',
  `sign_deadline`    DATETIME              DEFAULT NULL   COMMENT '签署截止时间',
  `revoke_reason`    VARCHAR(255)          DEFAULT NULL,
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_no` (`contract_no`),
  -- 出借协议按投资人 + 金额区分：同一标的下每个投资人各一份，且签署金额不同属不同合同，
  -- 这样"改了金额要求重新签署"只需新插一行，无需软删除旧行（避免软删除撞唯一键）
  UNIQUE KEY `uk_biz` (`biz_type`, `biz_id`, `contract_type`, `party_a_id`, `is_deleted`, `amount`),
  KEY `idx_party_a` (`party_a_id`, `status`),
  KEY `idx_party_b` (`party_b_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同实例';

-- ③ 签署存证（一次签名一条；uk_contract_signer 保证同一人同一合同只签一次）
CREATE TABLE IF NOT EXISTS `contract_sign_record` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `contract_id`    BIGINT       NOT NULL,
  `signer_id`      BIGINT       NOT NULL                COMMENT '签署人 userId',
  `signer_role`    VARCHAR(16)  NOT NULL                COMMENT 'PARTY_A / PARTY_B',
  `signer_name`    VARCHAR(64)  NOT NULL,
  `signer_id_card` VARCHAR(32)           DEFAULT NULL,
  `sign_img_url`   VARCHAR(255) NOT NULL                COMMENT '手写签名图 URL',
  `content_hash`   CHAR(64)     NOT NULL                COMMENT '签署时校验的正文哈希',
  `sign_ip`        VARCHAR(64)           DEFAULT NULL,
  `user_agent`     VARCHAR(255)          DEFAULT NULL,
  `sign_time`      DATETIME     NOT NULL,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_signer` (`contract_id`, `signer_id`),
  KEY `idx_signer` (`signer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同签署存证';

-- ---------- 内置模板（幂等插入：已存在同编号则不覆盖）----------
INSERT INTO `contract_template`
  (`template_no`, `contract_type`, `title`, `content_tpl`, `variables`, `version`, `status`)
SELECT * FROM (
  SELECT 'TPL_LOAN' AS a, 'LOAN' AS b, '借款合同' AS c,
    '借款合同\n\n合同编号：${contractNo}\n\n甲方（出借人）：旺旺金融平台投资人\n乙方（借款人）：${borrowerName}\n身份证号：${borrowerIdCard}\n\n一、借款金额：人民币 ${amount} 元（大写以系统记录为准）\n二、借款期限：${period} 期\n三、年化利率：${lendYearRate}%\n四、还款方式：按借款申请约定执行\n五、借款用途：${purpose}\n\n乙方承诺：所提供资料真实有效，按期还本付息；逾期按平台规则计收罚息并上报征信。\n本合同自乙方手写签署之日起生效，正文以系统快照为准，签署后不得单方修改。\n\n签署人（乙方）：____________    签署日期：${signDate}' AS d,
    'contractNo,borrowerName,borrowerIdCard,amount,period,lendYearRate,purpose,signDate' AS e,
    1 AS f, 1 AS g
) t
WHERE NOT EXISTS (SELECT 1 FROM `contract_template` x WHERE x.`template_no` = 'TPL_LOAN');

INSERT INTO `contract_template`
  (`template_no`, `contract_type`, `title`, `content_tpl`, `variables`, `version`, `status`)
SELECT * FROM (
  SELECT 'TPL_INVEST' AS a, 'INVEST' AS b, '出借协议（投资合同）' AS c,
    '出借协议\n\n协议编号：${contractNo}\n\n甲方（出借人/投资人）：${investorName}\n身份证号：${investorIdCard}\n乙方（借款人）：${borrowerName}\n中介服务平台：旺旺金融（仅提供信息中介服务，不承担代偿责任）\n\n一、出借金额：人民币 ${amount} 元\n二、标的编号：${lendNo}\n三、年化利率：${lendYearRate}%\n四、投资期限：${period} 期\n\n甲方确认：已充分阅读并理解上述标的的借款信息、风险等级与还款安排，\n知晓出借资金存在借款人违约、逾期等风险，自愿承担相应后果。\n本协议自甲方手写签署之日起生效。\n\n签署人（甲方）：____________    签署日期：${signDate}' AS d,
    'contractNo,investorName,investorIdCard,borrowerName,amount,lendNo,lendYearRate,period,signDate' AS e,
    1 AS f, 1 AS g
) t
WHERE NOT EXISTS (SELECT 1 FROM `contract_template` x WHERE x.`template_no` = 'TPL_INVEST');

INSERT INTO `contract_template`
  (`template_no`, `contract_type`, `title`, `content_tpl`, `variables`, `version`, `status`)
SELECT * FROM (
  SELECT 'TPL_PAYMENT' AS a, 'PAYMENT' AS b, '放款确认书' AS c,
    '放款确认书\n\n确认书编号：${contractNo}\n\n借款人：${borrowerName}\n标的编号：${lendNo}\n\n一、放款金额：人民币 ${amount} 元\n二、年化利率：${lendYearRate}%\n三、借款期限：${period} 期\n\n借款人确认：上述标的已募集完成，确认接受本次放款，\n并承诺自放款之日起按还款计划按期还本付息。\n\n签署人（借款人）：____________    签署日期：${signDate}' AS d,
    'contractNo,borrowerName,lendNo,amount,lendYearRate,period,signDate' AS e,
    1 AS f, 1 AS g
) t
WHERE NOT EXISTS (SELECT 1 FROM `contract_template` x WHERE x.`template_no` = 'TPL_PAYMENT');

-- ---------- 校验 ----------
SELECT 'contract_template' AS `table`, COUNT(*) AS `rows` FROM `contract_template`
UNION ALL SELECT 'contract', COUNT(*) FROM `contract`
UNION ALL SELECT 'contract_sign_record', COUNT(*) FROM `contract_sign_record`;
