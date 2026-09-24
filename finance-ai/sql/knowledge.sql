-- 知识库管理表（finance-ai模块）
-- 在finance_ai数据库执行

CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(100) NOT NULL COMMENT '知识库名称',
    `description` VARCHAR(500) DEFAULT '' COMMENT '描述',
    `embedding_model` VARCHAR(100) DEFAULT 'text-embedding-ada-002' COMMENT '向量模型',
    `doc_count` INT DEFAULT 0 COMMENT '文档数量',
    `chunk_count` INT DEFAULT 0 COMMENT '分块数量',
    `status` TINYINT DEFAULT 1 COMMENT '状态:0禁用 1启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库';

CREATE TABLE IF NOT EXISTS `knowledge_doc` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `kb_id` BIGINT NOT NULL COMMENT '知识库ID',
    `title` VARCHAR(200) NOT NULL COMMENT '文档标题',
    `source` VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '来源:pdf/text/url',
    `content` LONGTEXT COMMENT '文本内容',
    `file_name` VARCHAR(200) DEFAULT '' COMMENT 'PDF文件名',
    `file_size` BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    `chunk_count` INT DEFAULT 0 COMMENT '分块数量',
    `status` TINYINT DEFAULT 0 COMMENT '状态:0处理中 1就绪 2失败',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';