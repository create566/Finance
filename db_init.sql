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
