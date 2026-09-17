-- 标的结清凭证表
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
