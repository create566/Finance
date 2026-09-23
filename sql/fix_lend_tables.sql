-- ============================================
-- 标的表和投资记录表修复脚本
-- 执行方式：mysql -u root -p123456 ww_finance < fix_lend_tables.sql
-- ============================================

USE ww_finance;

-- ============================================
-- 1. 删除旧表（如果存在错误版本）
-- ============================================
DROP TABLE IF EXISTS `lend_item`;
DROP TABLE IF EXISTS `lend`;

-- ============================================
-- 2. 创建标的表（修正字段名，与实体类 Lend.java 对齐）
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
  `lend_year_rate` decimal(10,2) NOT NULL COMMENT '年化利率(百分数,7.0表示7%)【与实体类对齐】',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标的表（与实体类Lend.java完全对齐）';

-- ============================================
-- 3. 创建投资记录表（对应实体类 LendItem.java）
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资记录表（对应实体类LendItem.java）';

-- ============================================
-- 4. 验证表结构
-- ============================================
SELECT 
  TABLE_NAME,
  TABLE_COMMENT,
  CREATE_TIME
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'ww_finance' 
AND TABLE_NAME IN ('lend', 'lend_item')
ORDER BY TABLE_NAME;

-- ============================================
-- 5. 显示字段信息（确认字段名正确）
-- ============================================
SELECT 
  COLUMN_NAME,
  COLUMN_TYPE,
  COLUMN_COMMENT
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'ww_finance' 
AND TABLE_NAME = 'lend'
AND COLUMN_NAME = 'lend_year_rate';

SELECT COUNT(*) AS 'lend表字段总数' FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'ww_finance' AND TABLE_NAME = 'lend';

SELECT COUNT(*) AS 'lend_item表字段总数' FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'ww_finance' AND TABLE_NAME = 'lend_item';