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
    `bind_code`           VARCHAR(64)  DEFAULT NULL COMMENT '绑定账户协议号',
    `integral`            INT          DEFAULT 0 COMMENT '用户积分',
    `create_time`         DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`         DATETIME     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`          TINYINT(1)   DEFAULT 0 COMMENT '逻辑删除(1已删 0未删)',
    PRIMARY KEY (`id`)
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
    PRIMARY KEY (`id`)
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
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户绑定表';

-- 管理员表（后台登录用）
CREATE TABLE IF NOT EXISTS `sys_admin` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `username`     VARCHAR(50)  NOT NULL COMMENT '登录账号',
    `password`     VARCHAR(64)  NOT NULL COMMENT '密码(MD5)',
    `name`         VARCHAR(50)  DEFAULT NULL COMMENT '姓名',
    `avatar`       VARCHAR(255) DEFAULT NULL COMMENT '头像',
    `create_time`  DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`  DATETIME     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`   TINYINT(1)   DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员表';

-- 初始管理员：admin / 123456（密码 BCrypt 加密）
-- BCrypt 密码生成命令：passwordEncoder.encode("123456")
INSERT INTO `sys_admin` (`username`, `password`, `name`, `create_time`)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '超级管理员', NOW())
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