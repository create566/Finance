-- ============================================
-- www_finance 本地开发库初始化脚本
-- 对 localhost:3306 (root/123456) 执行本文件
-- ============================================

CREATE DATABASE IF NOT EXISTS www_finance DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE www_finance;

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
    `user_id`      BIGINT(20) NOT NULL COMMENT '用户ID',
    `username`     VARCHAR(50) DEFAULT NULL COMMENT '用户名/手机号',
    `login_type`   INT(11) DEFAULT 0 COMMENT '登录类型(0前台 1后台)',
    `login_ip`     VARCHAR(64) DEFAULT NULL COMMENT '登录IP',
    `login_time`   DATETIME NOT NULL COMMENT '登录时间',
    `login_result` INT(11) DEFAULT 1 COMMENT '登录结果(0失败 1成功)',
    `user_agent`   VARCHAR(255) DEFAULT NULL COMMENT '用户代理',
    `create_time`  DATETIME NOT NULL COMMENT '创建时间',
    `update_time`  DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录日志表';
