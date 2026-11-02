-- ============================================
-- www_finance 本地开发库初始化脚本
-- 对 localhost:3306 (root/123456) 执行本文件
-- ============================================

CREATE DATABASE IF NOT EXISTS ww_finance DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ww_finance;

-- 用户表（对应 com.example.demo.entity.User）
CREATE TABLE IF NOT EXISTS `user` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_type`           INT          DEFAULT NULL COMMENT '用户类型',
    `mobile`              VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `password`            VARCHAR(64)  DEFAULT NULL COMMENT '密码(MD5)',
    `name`                VARCHAR(50)  DEFAULT NULL COMMENT '姓名',
    `status`              INT          DEFAULT 1 COMMENT '状态(1正常)',
    `nick_name`           VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `id_card`             VARCHAR(20)  DEFAULT NULL COMMENT '身份证号',
    `email`               VARCHAR(50)  DEFAULT NULL COMMENT '邮箱',
    `openid`              VARCHAR(64)  DEFAULT NULL COMMENT '微信openid',
    `head_img`            VARCHAR(255) DEFAULT NULL COMMENT '头像',
    `bind_status`         INT          DEFAULT 0 COMMENT '绑定状态(0未绑定 1绑定成功 -1失败)',
    `borrow_auth_status`  INT          DEFAULT 0 COMMENT '认证状态(0未认证 1认证中 2通过 -1失败)',
    `invest_auth_status`  INT          DEFAULT 0 COMMENT '投资人认证状态(0未认证 1认证中 2通过 -1失败)',
    `bind_code`           VARCHAR(64)  DEFAULT NULL COMMENT '绑定账户协议号',
    `integral`            INT          DEFAULT 0 COMMENT '用户积分',
    `create_time`         DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`         DATETIME     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`          TINYINT(1)   DEFAULT 0 COMMENT '逻辑删除(1已删 0未删)',
    PRIMARY KEY (`id`),
    -- 手机号唯一：一个手机号只能注册一个账号（应用层查重非原子，必须由数据库兜底）
    UNIQUE KEY `uk_mobile` (`mobile`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 用户账户表（对应 com.example.demo.entity.UserAccount）
CREATE TABLE IF NOT EXISTS `user_account` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`       BIGINT         DEFAULT NULL COMMENT '用户id',
    `amount`        DECIMAL(20, 2) DEFAULT 0.00 COMMENT '可用余额',
    `freeze_amount` DECIMAL(20, 2) DEFAULT 0.00 COMMENT '冻结金额',
    `create_time`   DATETIME       DEFAULT NULL COMMENT '创建时间',
    `update_time`   DATETIME       DEFAULT NULL COMMENT '更新时间',
    `is_deleted`    TINYINT(1)     DEFAULT 0 COMMENT '逻辑删除(1已删 0未删)',
    `version`       INT            DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (`id`),
    -- P5-27：此唯一键此前只存在于线上库、脚本未声明（按脚本重建库会丢失，并发可插出多账户）
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户账户表';

-- 借款信息表（对应 com.example.demo.entity.BorrowInfo）
CREATE TABLE IF NOT EXISTS `borrow_info` (
    `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`          BIGINT         DEFAULT NULL COMMENT '借款用户id',
    `amount`           DECIMAL(20, 2) DEFAULT NULL COMMENT '借款金额',
    `period`           INT            DEFAULT NULL COMMENT '借款期限',
    `borrow_year_rate` DECIMAL(10, 6) DEFAULT NULL COMMENT '年化利率',
    `return_method`    INT            DEFAULT NULL COMMENT '还款方式 1等额本息 2等额本金 3每月还息一次还本 4一次还本',
    `money_use`        INT            DEFAULT NULL COMMENT '资金用途',
    `status`           INT            DEFAULT 0 COMMENT '状态(0未提交 1审核中 2通过 -1不通过)',
    `create_time`      DATETIME       DEFAULT NULL COMMENT '创建时间',
    `update_time`      DATETIME       DEFAULT NULL COMMENT '更新时间',
    `is_deleted`       TINYINT(1)     DEFAULT 0 COMMENT '逻辑删除(1已删 0未删)',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '借款信息表';

-- 用户绑定表（对应 com.example.demo.entity.UserBind）
CREATE TABLE IF NOT EXISTS `user_bind` (
    `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`          BIGINT         DEFAULT NULL COMMENT '用户id',
    `name`             VARCHAR(50)    DEFAULT NULL COMMENT '姓名',
    `bank_type`        VARCHAR(50)    DEFAULT NULL COMMENT '银行类型',
    `mobile`           VARCHAR(20)    DEFAULT NULL COMMENT '手机号',
    `bind_code`        VARCHAR(64)    DEFAULT NULL COMMENT '绑定协议号',
    `id_card`          VARCHAR(20)    DEFAULT NULL COMMENT '身份证号',
    `bank_no`          VARCHAR(30)    DEFAULT NULL COMMENT '银行卡号',
    `status`           INT            DEFAULT 0 COMMENT '状态(0未绑定 1绑定成功)',
    `create_time`      DATETIME       DEFAULT NULL COMMENT '创建时间',
    `update_time`      DATETIME       DEFAULT NULL COMMENT '更新时间',
    `is_deleted`       TINYINT(1)     DEFAULT 0 COMMENT '逻辑删除(1已删 0未删)',
    PRIMARY KEY (`id`),
    -- P5-27：同 uk_user_id，一个用户只能有一条绑卡记录
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户绑定表';

-- 管理员表（后台登录用）
CREATE TABLE IF NOT EXISTS `sys_admin` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `username`     VARCHAR(50)  NOT NULL COMMENT '登录账号',
    `password`     VARCHAR(64)  NOT NULL COMMENT '密码(MD5)',
    `name`         VARCHAR(50)  DEFAULT NULL COMMENT '姓名',
    `avatar`       VARCHAR(255) DEFAULT NULL COMMENT '头像',
    `role`         VARCHAR(32)  NOT NULL DEFAULT 'ADMIN' COMMENT '后台角色:SUPER_ADMIN/ADMIN',
    `create_time`  DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`  DATETIME     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`   TINYINT(1)   DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员表';

-- 初始管理员：admin / 123456（密码 BCrypt 加密，明文 123456）
-- role 显式给 SUPER_ADMIN：新环境只有这一个账号，不给最高权限则放款/下架无法演示。
INSERT INTO `sys_admin` (`username`, `password`, `name`, `role`, `create_time`)
VALUES ('admin', '$2b$10$3945YgHYWC0u.vYHybs.5OZlTydLPIsAgKjICtl771wpdNrUnA0lq', '超级管理员', 'SUPER_ADMIN', NOW())
ON DUPLICATE KEY UPDATE `username` = `username`;

-- ============================================
-- 标的结清凭证表
-- ============================================
CREATE TABLE IF NOT EXISTS `lend_settle_record` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '编号',
    `lend_id` BIGINT(20) NOT NULL COMMENT '标的ID',
    `lend_no` VARCHAR(64) NOT NULL COMMENT '标的编号',
    `borrower_id` BIGINT(20) NOT NULL COMMENT '借款人ID',
    `total_principal` DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '本金合计',
    `total_interest` DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '利息合计',
    `total_penalty` DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '罚息合计',
    `settle_time` DATETIME NOT NULL COMMENT '结清时间',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_lend_id` (`lend_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标的结清凭证表';

-- ============================================
-- 对账差异表
-- ============================================
CREATE TABLE IF NOT EXISTS `reconciliation_record` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '编号',
    `trans_no` VARCHAR(64) NOT NULL COMMENT '交易单号',
    `trans_type` INT(11) NOT NULL COMMENT '交易类型（1：充值 2：提现 3：投标 4：回款 5：放款 6：还款）',
    `trans_type_name` VARCHAR(32) DEFAULT NULL COMMENT '交易类型名称',
    `user_id` BIGINT(20) NOT NULL COMMENT '用户id',
    `user_name` VARCHAR(64) DEFAULT NULL COMMENT '用户名称',
    `amount` DECIMAL(16,2) NOT NULL COMMENT '交易金额',
    `bank_trans_no` VARCHAR(64) DEFAULT NULL COMMENT '银行流水号',
    `status` INT(11) NOT NULL COMMENT '状态（1：本地缺失 2：金额不一致）',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '差异说明',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_trans_no` (`trans_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账差异记录表';

-- ============================================
-- 用户登录日志表
-- ============================================
CREATE TABLE IF NOT EXISTS `user_login_record` (
    `id`           BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`      BIGINT(20) DEFAULT NULL COMMENT '用户ID',
    `ip`           VARCHAR(32) DEFAULT NULL COMMENT '登录IP',
    `login_result` TINYINT(1) DEFAULT NULL COMMENT '登录结果 0失败 1成功',
    `login_type`   TINYINT(1) DEFAULT NULL COMMENT '登录方式 0密码 1验证码',
    `create_time`  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录日志表';

-- ============================================
-- 标的表（对应 com.example.demo.entity.Lend）
-- ============================================
CREATE TABLE IF NOT EXISTS `lend` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `lend_no` varchar(32) NOT NULL COMMENT '标的编号',
  `title` varchar(128) NOT NULL COMMENT '标的名称',
  `user_id` bigint(20) NOT NULL COMMENT '借款人ID',
  `borrow_info_id` bigint(20) DEFAULT NULL COMMENT '关联借款申请ID',
  `amount` decimal(20,2) NOT NULL COMMENT '标的金额',
  `invest_amount` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '已募集金额',
  `invest_num` int(11) NOT NULL DEFAULT '0' COMMENT '投资人数',
  `period` int(11) NOT NULL COMMENT '期限(月)',
  `lend_year_rate` decimal(10,2) NOT NULL COMMENT '年化利率(百分数,7.0表示7%)',
  `service_rate` decimal(10,2) DEFAULT NULL COMMENT '平台服务费率',
  `return_method` tinyint(4) NOT NULL COMMENT '还款方式 1等额本息 2等额本金 3先息后本 4一次还本',
  `lowest_amount` decimal(20,2) DEFAULT '100.00' COMMENT '最低起投金额',
  `money_use` tinyint(4) DEFAULT NULL COMMENT '资金用途',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态 0待发布 1募资中 2已完成 3已逾期 4已下架',
  `risk_level` tinyint(4) DEFAULT '1' COMMENT '风险等级 1低 2中 3高',
  `lend_info` varchar(512) DEFAULT NULL COMMENT '标的描述',
  `publish_date` datetime DEFAULT NULL COMMENT '发布时间',
  `lend_start_date` date DEFAULT NULL COMMENT '放款开始时间',
  `lend_end_date` date DEFAULT NULL COMMENT '募集截止时间',
  `expect_amount` decimal(20,2) DEFAULT NULL COMMENT '预期募集金额',
  `real_amount` decimal(20,2) DEFAULT NULL COMMENT '实际募集金额',
  `check_time` datetime DEFAULT NULL COMMENT '审核时间',
  `check_admin_id` bigint(20) DEFAULT NULL COMMENT '审核管理员ID',
  `payment_time` datetime DEFAULT NULL COMMENT '放款时间',
  `payment_admin_id` bigint(20) DEFAULT NULL COMMENT '放款管理员ID',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(1已删 0未删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lend_no` (`lend_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_borrow_info_id` (`borrow_info_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标的表';

-- ============================================
-- 投资记录表（对应 com.example.demo.entity.LendItem）
-- ============================================
CREATE TABLE IF NOT EXISTS `lend_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `lend_item_no` varchar(64) NOT NULL COMMENT '投资记录编号',
  `lend_id` bigint(20) NOT NULL COMMENT '标的ID',
  `invest_user_id` bigint(20) NOT NULL COMMENT '投资人ID',
  `invest_name` varchar(64) DEFAULT NULL COMMENT '投资人姓名',
  `invest_amount` decimal(20,2) NOT NULL COMMENT '投资金额',
  `lend_year_rate` decimal(10,2) NOT NULL COMMENT '年化利率（从标的拷贝快照）',
  `invest_time` datetime DEFAULT NULL COMMENT '投资时间',
  `lend_start_date` date DEFAULT NULL COMMENT '起息日期',
  `lend_end_date` date DEFAULT NULL COMMENT '到期日期',
  `expect_amount` decimal(20,2) DEFAULT NULL COMMENT '预期收益',
  `real_amount` decimal(20,2) DEFAULT NULL COMMENT '实际收益',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态 0已投标 1已撤标 2已满标 3已结算',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(1已删 0未删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lend_item_no` (`lend_item_no`),
  KEY `idx_lend_id` (`lend_id`),
  KEY `idx_invest_user_id` (`invest_user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资记录表';

-- ============================================
-- 借款人档案表（对应 com.example.demo.entity.Borrower）
-- ============================================
CREATE TABLE IF NOT EXISTS `borrower` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `name` VARCHAR(64) DEFAULT NULL COMMENT '真实姓名',
  `id_card` VARCHAR(18) DEFAULT NULL COMMENT '身份证号',
  `mobile` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `sex` TINYINT(4) DEFAULT NULL COMMENT '性别',
  `age` INT(11) DEFAULT NULL COMMENT '年龄',
  `education` TINYINT(4) DEFAULT NULL COMMENT '学历',
  `is_marry` TINYINT(4) DEFAULT NULL COMMENT '婚姻状况',
  `industry` TINYINT(4) DEFAULT NULL COMMENT '行业',
  `income` TINYINT(4) DEFAULT NULL COMMENT '收入',
  `return_source` TINYINT(4) DEFAULT NULL COMMENT '还款来源',
  `contacts_name` VARCHAR(64) DEFAULT NULL COMMENT '联系人姓名',
  `contacts_mobile` VARCHAR(20) DEFAULT NULL COMMENT '联系人手机号',
  `contacts_relation` TINYINT(4) DEFAULT NULL COMMENT '联系人关系',
  `status` TINYINT(4) DEFAULT 0 COMMENT '状态 0未认证 1认证中 2认证通过 -1认证失败',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  -- P5-27：脚本原写 `KEY idx_user_id`，线上库实为唯一键；一个用户只能有一份借款人档案
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借款人档案表';

-- ============================================
-- 借款人附件表（对应 com.example.demo.entity.BorrowerAttach）
-- ============================================
CREATE TABLE IF NOT EXISTS `borrower_attach` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `borrower_id` BIGINT(20) NOT NULL COMMENT '借款人ID',
  `image_type` VARCHAR(32) DEFAULT NULL COMMENT '图片类型 idCard1/idCard2/house/car',
  `image_url` VARCHAR(512) DEFAULT NULL COMMENT '图片地址',
  `image_name` VARCHAR(128) DEFAULT NULL COMMENT '图片名称',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_borrower_id` (`borrower_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借款人附件表';

-- ============================================
-- 投资人档案表（对应 com.example.demo.entity.Investor）
-- ============================================
CREATE TABLE IF NOT EXISTS `investor` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `name` VARCHAR(64) DEFAULT NULL COMMENT '真实姓名',
  `id_card` VARCHAR(18) DEFAULT NULL COMMENT '身份证号',
  `mobile` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `sex` TINYINT(4) DEFAULT NULL COMMENT '性别',
  `age` INT(11) DEFAULT NULL COMMENT '年龄',
  `status` TINYINT(4) DEFAULT 0 COMMENT '状态 0未认证 1认证中 2认证通过 -1认证失败',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资人档案表';

-- ============================================
-- 投资人附件表（对应 com.example.demo.entity.InvestorAttach）
-- ============================================
CREATE TABLE IF NOT EXISTS `investor_attach` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `investor_id` BIGINT(20) NOT NULL COMMENT '投资人ID',
  `image_type` VARCHAR(32) DEFAULT NULL COMMENT '图片类型 idCard1/idCard2',
  `image_url` VARCHAR(512) DEFAULT NULL COMMENT '图片地址',
  `image_name` VARCHAR(128) DEFAULT NULL COMMENT '图片名称',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_investor_id` (`investor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资人附件表';

-- ============================================
-- 数据字典表（对应 com.example.demo.entity.Dict）
-- ============================================
CREATE TABLE IF NOT EXISTS `dict` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` BIGINT(20) DEFAULT NULL COMMENT '父节点ID（选项节点指向分类节点）',
  `dict_code` VARCHAR(64) DEFAULT NULL COMMENT '字典分类编码（仅分类节点有值）',
  `name` VARCHAR(64) DEFAULT NULL COMMENT '显示名称',
  `value` INT(11) DEFAULT NULL COMMENT '存储值（选项节点）',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  -- P5-27：同一分类下的选项 value 不得重复（此前只在库里）
  UNIQUE KEY `uk_parent_id_value` (`parent_id`, `value`),
  KEY `idx_dict_code` (`dict_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典表';

-- ============================================
-- 积分等级表（对应 com.example.demo.entity.IntegralGrade）
-- ============================================
CREATE TABLE IF NOT EXISTS `integral_grade` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `integral_start` INT(11) DEFAULT NULL COMMENT '积分下限',
  `integral_end` INT(11) DEFAULT NULL COMMENT '积分上限',
  `borrow_amount` DECIMAL(20,2) DEFAULT NULL COMMENT '可借额度',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分等级表';

-- ============================================
-- 还款计划表（对应 com.example.demo.entity.LendReturn）
-- ============================================
CREATE TABLE IF NOT EXISTS `lend_return` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `lend_id` BIGINT(20) NOT NULL COMMENT '标的ID',
  `borrow_info_id` BIGINT(20) DEFAULT NULL COMMENT '借款申请ID',
  `return_no` VARCHAR(64) DEFAULT NULL COMMENT '还款编号',
  `user_id` BIGINT(20) NOT NULL COMMENT '借款人ID',
  `amount` DECIMAL(20,2) DEFAULT NULL COMMENT '标的总金额',
  `base_amount` DECIMAL(20,2) DEFAULT NULL COMMENT '剩余本金',
  `current_period` INT(11) DEFAULT NULL COMMENT '当前期数',
  `lend_year_rate` DECIMAL(10,2) DEFAULT NULL COMMENT '年化利率',
  `return_method` TINYINT(4) DEFAULT NULL COMMENT '还款方式',
  `principal` DECIMAL(20,2) DEFAULT NULL COMMENT '本期本金',
  `interest` DECIMAL(20,2) DEFAULT NULL COMMENT '本期利息',
  `total` DECIMAL(20,2) DEFAULT NULL COMMENT '本期应还总额',
  `fee` DECIMAL(20,2) DEFAULT NULL COMMENT '手续费',
  `return_date` DATE DEFAULT NULL COMMENT '应还日期',
  `real_return_time` DATETIME DEFAULT NULL COMMENT '实际还款时间',
  `is_overdue` TINYINT(1) DEFAULT 0 COMMENT '是否逾期',
  `overdue_total` DECIMAL(20,2) DEFAULT NULL COMMENT '逾期金额（含罚息）',
  `is_last` TINYINT(1) DEFAULT 0 COMMENT '是否最后一期',
  `status` TINYINT(4) DEFAULT 0 COMMENT '状态 0未还 1已还 2逾期',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  -- P5-27：还款编号唯一，防止重复生成本期计划（此前只在库里）
  UNIQUE KEY `uk_return_no` (`return_no`),
  KEY `idx_lend_id` (`lend_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='还款计划表';

-- ============================================
-- 投资人收益计划表（对应 com.example.demo.entity.LendItemReturn）
-- ============================================
CREATE TABLE IF NOT EXISTS `lend_item_return` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `lend_return_id` BIGINT(20) NOT NULL COMMENT '还款计划ID',
  `lend_item_id` BIGINT(20) NOT NULL COMMENT '投资记录ID',
  `lend_id` BIGINT(20) NOT NULL COMMENT '标的ID',
  `invest_user_id` BIGINT(20) NOT NULL COMMENT '投资人ID',
  `invest_amount` DECIMAL(20,2) DEFAULT NULL COMMENT '投资本金',
  `current_period` INT(11) DEFAULT NULL COMMENT '当前期数',
  `lend_year_rate` DECIMAL(10,2) DEFAULT NULL COMMENT '年化利率',
  `return_method` TINYINT(4) DEFAULT NULL COMMENT '还款方式',
  `principal` DECIMAL(20,2) DEFAULT NULL COMMENT '本期应得本金',
  `interest` DECIMAL(20,2) DEFAULT NULL COMMENT '本期应得利息',
  `total` DECIMAL(20,2) DEFAULT NULL COMMENT '本期应得总额',
  `fee` DECIMAL(20,2) DEFAULT NULL COMMENT '手续费',
  `return_date` DATE DEFAULT NULL COMMENT '应还日期',
  `real_return_time` DATETIME DEFAULT NULL COMMENT '实际回款时间',
  `is_overdue` TINYINT(1) DEFAULT 0 COMMENT '是否逾期',
  `overdue_total` DECIMAL(20,2) DEFAULT NULL COMMENT '逾期金额',
  `status` TINYINT(4) DEFAULT 0 COMMENT '状态 0未还 1已还',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_lend_return_id` (`lend_return_id`),
  KEY `idx_invest_user_id` (`invest_user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资人收益计划表';

-- ============================================
-- 交易流水表（对应 com.example.demo.entity.TransFlow）
-- ============================================
CREATE TABLE IF NOT EXISTS `trans_flow` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户id',
  `user_name` VARCHAR(64) DEFAULT NULL COMMENT '用户名称',
  `trans_no` VARCHAR(64) NOT NULL COMMENT '交易单号（唯一，防重复提交）',
  `trans_type` TINYINT(4) DEFAULT NULL COMMENT '交易类型（1充值 2提现 3投标 4回款 5放款 6还款）',
  `trans_type_name` VARCHAR(32) DEFAULT NULL COMMENT '交易类型名称',
  `trans_amount` DECIMAL(20,2) DEFAULT NULL COMMENT '交易金额',
  `memo` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trans_no` (`trans_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_trans_type` (`trans_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易流水表';

-- ============================================
-- 用户积分明细表（对应 com.example.demo.entity.UserIntegral）
-- ============================================
CREATE TABLE IF NOT EXISTS `user_integral` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `integral` INT(11) DEFAULT NULL COMMENT '积分变动值（正加负减）',
  `content` VARCHAR(255) DEFAULT NULL COMMENT '变动说明',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分明细表';

-- ============================================
-- 站内消息表（对应 com.example.demo.entity.Message）
-- ============================================
CREATE TABLE IF NOT EXISTS `message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '接收用户ID',
  `title` VARCHAR(100) DEFAULT NULL COMMENT '消息标题',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '消息内容',
  `message_type` TINYINT DEFAULT 1 COMMENT '类型：1逾期催收 2系统通知 3审批通知',
  `is_read` TINYINT(1) DEFAULT 0 COMMENT '是否已读：0未读 1已读',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_read` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息表';