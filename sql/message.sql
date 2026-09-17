-- 站内消息表（逾期催收/系统通知/审批通知）
CREATE TABLE IF NOT EXISTS `message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '接收用户ID',
  `title` VARCHAR(100) DEFAULT NULL COMMENT '消息标题',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '消息内容',
  `message_type` TINYINT DEFAULT 1 COMMENT '类型：1逾期催收 2系统通知 3审批通知',
  `is_read` TINYINT(1) DEFAULT 0 COMMENT '是否已读：0未读 1已读',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_read` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息表';
