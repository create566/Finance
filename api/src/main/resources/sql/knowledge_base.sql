-- 知识库表
CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '知识库名称',
  `description` varchar(500) DEFAULT '' COMMENT '描述',
  `doc_count` int(11) NOT NULL DEFAULT 0 COMMENT '文档数量',
  `chunk_count` int(11) NOT NULL DEFAULT 0 COMMENT '分块数量',
  `embedding_model` varchar(100) NOT NULL DEFAULT 'text-embedding-ada-002' COMMENT '向量模型',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0-未启用 1-启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 知识文档表
CREATE TABLE IF NOT EXISTS `knowledge_doc` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `kb_id` bigint(20) NOT NULL COMMENT '知识库ID',
  `title` varchar(200) NOT NULL COMMENT '文档标题',
  `source` varchar(20) NOT NULL COMMENT '来源类型：pdf/text/url',
  `content` longtext COMMENT '文本内容（text类型）',
  `file_name` varchar(200) DEFAULT '' COMMENT '文件名（PDF类型）',
  `file_size` int(11) NOT NULL DEFAULT 0 COMMENT '文件大小（KB）',
  `file_url` varchar(500) DEFAULT '' COMMENT '文件存储路径',
  `chunk_count` int(11) NOT NULL DEFAULT 0 COMMENT '分块数量',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0-处理中 1-就绪 2-失败',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_kb_id` (`kb_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档表';

-- 初始化示例数据
INSERT INTO `knowledge_base` (`name`, `description`, `doc_count`, `chunk_count`, `embedding_model`, `status`) VALUES
('金融风控知识库', '包含风控规则、反欺诈策略等文档', 12, 348, 'text-embedding-ada-002', 1),
('产品手册库', '信贷产品说明、利率手册等', 5, 86, 'text-embedding-ada-002', 1),
('法规政策库', '金融法规、监管政策文件', 3, 52, 'm3e-base', 0);

INSERT INTO `knowledge_doc` (`kb_id`, `title`, `source`, `content`, `file_name`, `file_size`, `chunk_count`, `status`) VALUES
(1, '反欺诈策略手册', 'pdf', '', 'anti-fraud.pdf', 2048, 35, 1),
(1, '信用评分模型说明', 'text', '信用评分模型基于用户多维度数据进行综合评估，包括但不限于：还款历史、负债比、信用历史长度、新增信用查询、信用类型组合等维度。FICO评分范围300-850分，分数越高代表信用风险越低。', '', 0, 12, 1),
(1, '风险等级划分标准', 'text', '风险等级分为低、中、高三级。低风险：信用评分>700，月收入>2万，负债比<30%。中风险：信用评分600-700，月收入1-2万，负债比30-50%。高风险：信用评分<600或负债比>50%。', '', 0, 8, 1),
(2, '信贷产品利率表', 'pdf', '', 'rate-table.pdf', 512, 6, 1),
(1, '催收流程规范', 'pdf', '', 'collection-flow.pdf', 1536, 28, 0);