-- ============================================
-- 智能客服相关表 (MySQL)
-- ============================================

-- 1. 知识切片元数据表 (切片管理，双向关联 ChromaDB)
CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID (即 chunk_id，与 ChromaDB metadata 中的 chunk_id 对应)',
  `doc_id` bigint(20) NOT NULL COMMENT '所属文档ID',
  `kb_id` bigint(20) NOT NULL COMMENT '所属知识库ID',
  `chunk_index` int(11) NOT NULL DEFAULT 0 COMMENT '切片序号(从0开始)',
  `summary` varchar(500) DEFAULT '' COMMENT '切片摘要(前200字)',
  `chunk_size` int(11) NOT NULL DEFAULT 0 COMMENT '切片字符数',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`doc_id`),
  KEY `idx_kb_id` (`kb_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识切片元数据表';

-- 2. 提示词配置表 (管理端动态修改提示词，AI模块拉取)
CREATE TABLE IF NOT EXISTS `prompt_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `prompt_key` varchar(100) NOT NULL COMMENT '指令标识(程序引用，唯一)',
  `name` varchar(100) NOT NULL COMMENT '指令名称(展示用)',
  `scene` varchar(50) NOT NULL COMMENT '使用场景: chat/audit/risk/summary',
  `content_type` varchar(20) NOT NULL DEFAULT 'prompt' COMMENT '内容类型: prompt-系统指令 / keywords-关键词列表',
  `system_prompt` text NOT NULL COMMENT '系统指令全文 / 关键词列表(每行一个)',
  `temperature` double NOT NULL DEFAULT 0.7 COMMENT 'LLM温度参数',
  `max_tokens` int(11) NOT NULL DEFAULT 2048 COMMENT '最大输出token数',
  `top_p` double NOT NULL DEFAULT 0.9 COMMENT 'LLM top_p 参数',
  `model` varchar(100) DEFAULT '-' COMMENT '绑定的模型名称',
  `is_default` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否默认：1-默认 0-非默认',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_key` (`prompt_key`),
  KEY `idx_scene` (`scene`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词配置表';

-- 3. AI模型配置表
CREATE TABLE IF NOT EXISTS `ai_model_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '模型名称',
  `provider` varchar(50) NOT NULL COMMENT '提供商: openai/anthropic/zhipu/qwen/baichuan/local/dashscope',
  `model_type` varchar(20) NOT NULL COMMENT '模型类型: chat/embedding/rerank',
  `model_name` varchar(100) NOT NULL COMMENT '模型标识: gpt-4/qwen-turbo/text-embedding-ada-002',
  `api_url` varchar(500) DEFAULT '' COMMENT 'API地址',
  `api_key` varchar(200) DEFAULT '' COMMENT 'API Key',
  `max_tokens` int(11) NOT NULL DEFAULT 4096 COMMENT '最大上下文token数',
  `temperature` double NOT NULL DEFAULT 0.7 COMMENT '温度参数',
  `is_default` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否默认模型：1-是 0-否',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_model_type` (`model_type`),
  KEY `idx_provider` (`provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置表';

-- 4. 对话场景-模型-提示词-知识库绑定表
CREATE TABLE IF NOT EXISTS `ai_chat_model_binding` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scene` varchar(50) NOT NULL COMMENT '场景标识: intent/qa/chitchat/summary/safety',
  `scene_name` varchar(100) NOT NULL COMMENT '场景名称',
  `model_id` bigint(20) NOT NULL COMMENT '绑定的模型ID',
  `model_name` varchar(100) DEFAULT '' COMMENT '模型名称(冗余)',
  `prompt_id` bigint(20) NOT NULL COMMENT '绑定的提示词ID',
  `prompt_key` varchar(100) DEFAULT '' COMMENT '提示词标识(冗余)',
  `kb_id` bigint(20) DEFAULT NULL COMMENT '绑定的知识库ID(NULL=不检索)',
  `kb_name` varchar(100) DEFAULT '—' COMMENT '知识库名称(冗余)',
  `enabled` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否启用：1-是 0-否',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scene` (`scene`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话场景绑定表';

-- 5. 客服会话表
CREATE TABLE IF NOT EXISTS `chat_session` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `user_phone` varchar(20) DEFAULT '' COMMENT '用户手机号(冗余)',
  `user_name` varchar(50) DEFAULT '' COMMENT '用户姓名(冗余)',
  `title` varchar(200) DEFAULT '' COMMENT '会话标题(首条提问截取)',
  `message_count` int(11) NOT NULL DEFAULT 0 COMMENT '消息条数',
  `model_name` varchar(100) DEFAULT '' COMMENT '使用的模型名称',
  `kb_name` varchar(100) DEFAULT '' COMMENT '命中的知识库',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0-进行中 1-已结束',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话表';

-- 6. 聊天消息表
CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` bigint(20) NOT NULL COMMENT '会话ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `role` varchar(20) NOT NULL COMMENT '角色: user-用户提问 / assistant-AI回答',
  `content` text NOT NULL COMMENT '消息内容',
  `chunk_ids` varchar(500) DEFAULT '' COMMENT '引用的知识库切片ID列表(JSON数组)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- ============================================
-- 初始化示例数据
-- ============================================

-- 模型配置
INSERT INTO `ai_model_config` (`name`, `provider`, `model_type`, `model_name`, `api_url`, `api_key`, `max_tokens`, `temperature`, `is_default`, `status`, `remark`) VALUES
('GPT-4 对话模型', 'openai', 'chat', 'gpt-4', 'https://api.openai.com/v1/chat/completions', 'sk-****************************abcd', 8192, 0.7, 1, 1, '主力对话模型'),
('Ada 嵌入模型', 'openai', 'embedding', 'text-embedding-ada-002', 'https://api.openai.com/v1/embeddings', 'sk-****************************abcd', 8191, 0, 1, 1, '知识库默认向量模型'),
('GLM-4 对话模型', 'zhipu', 'chat', 'glm-4', 'https://open.bigmodel.cn/api/paas/v4/chat/completions', '****************************xyz', 4096, 0.7, 0, 1, '国产备选模型'),
('M3E 嵌入模型', 'local', 'embedding', 'm3e-base', 'http://localhost:8081/embed', '', 512, 0, 0, 0, '本地部署向量模型'),
('BGE Rerank 重排模型', 'local', 'rerank', 'bge-reranker-large', 'http://localhost:8082/rerank', '', 512, 0, 1, 1, '检索结果重排'),
('Qwen-Turbo 对话模型', 'dashscope', 'chat', 'qwen-turbo', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 8192, 0.7, 0, 1, '阿里云百炼快速对话'),
('Qwen-Plus 对话模型', 'dashscope', 'chat', 'qwen-plus', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 8192, 0.2, 0, 1, 'RAG知识问答推荐模型');

-- 提示词配置
INSERT INTO `prompt_config` (`prompt_key`, `name`, `scene`, `content_type`, `system_prompt`, `temperature`, `max_tokens`, `top_p`, `model`, `is_default`, `status`, `remark`) VALUES
('CS_SYSTEM_PROMPT', '客服系统指令', 'chat', 'prompt', '你是一个专业的金融客服助手，服务于「旺旺信贷」平台。\n\n你的职责：\n1. 解答用户关于借款、还款、利率、信用等级等问题\n2. 提供产品咨询和操作指引\n3. 引导用户完成相关业务流程\n\n注意事项：\n- 回答要简洁、准确、友好\n- 涉及具体利率和额度时，优先从知识库检索最新信息\n- 不要承诺未经审核批准的借款\n- 如遇到超出业务范围的问题，引导用户联系人工客服 400-xxx-xxxx\n\n回答格式：\n- 使用中文\n- 适当使用列表和加粗\n- 金额使用 ¥ 符号', 0.7, 2048, 0.9, 'gpt-4', 1, 1, '客服对话主指令'),
('INTENT_SYSTEM_PROMPT', '意图识别指令', 'chat', 'prompt', '你是一个意图识别助手，负责分析用户输入的意图类别。\n\n意图类别：\n1. borrow_consult - 借款咨询\n2. repay_consult - 还款咨询\n3. account_consult - 账户咨询\n4. product_consult - 产品咨询\n5. complaint - 投诉建议\n6. chitchat - 闲聊问候\n7. handoff - 请求人工客服\n8. other - 其他\n\n输出格式（JSON）：\n{\n  "intent": "意图类别",\n  "confidence": 0.95,\n  "entities": [],\n  "reply_hint": "回复建议"\n}', 0.2, 512, 0.8, 'gpt-4', 0, 1, '意图识别路由'),
('UNSAFE_KEYWORDS', '不安全关键词', 'risk', 'keywords', '赌博\n色情\n暴力\n传销\n诈骗\n洗钱\n高利贷\n套现\n黑客\n刷单\n私下交易\n场外配资\n非法集资\n庞氏骗局', 0, 0, 0, '-', 1, 1, '安全拦截关键词'),
('HANDOFF_KEYWORDS', '转人工关键词', 'chat', 'keywords', '转人工\n人工客服\n找人工\n真人客服\n找客服\n转接人工\n人工服务\n客服人员\n联系客服\n找工作人员\n找人工客服\n接人工\n要人工\n不想跟机器人聊', 0, 0, 0, '-', 1, 1, '转人工触发关键词'),
('csChitchatClient', '闲聊客户端指令', 'chat', 'prompt', '你是一个友好的闲聊助手，负责处理用户的日常寒暄和闲聊。\n\n适用场景：\n- 问候\n- 感谢\n- 天气闲聊\n- 情感表达\n- 笑话/趣味对话\n\n注意事项：\n- 保持轻松友好的语调\n- 回复简短自然\n- 不要主动引导到金融业务话题\n- 涉及金融专业问题时不要随意回答', 0.9, 256, 0.95, 'gpt-4', 0, 1, '闲聊专用'),
('RISK_SYSTEM_PROMPT', '风控审核指令', 'risk', 'prompt', '你是一个金融风控分析助手。\n\n职责：\n1. 根据用户信息评估风险等级\n2. 给出风险提示和建议\n3. 识别潜在欺诈行为\n\n风险等级：\n- 低风险：信用评分>700，月收入>2万，负债比<30%\n- 中风险：信用评分600-700，月收入1-2万，负债比30-50%\n- 高风险：信用评分<600，负债比>50%', 0.3, 1024, 0.85, 'gpt-4', 0, 1, '风控分析'),
('AUDIT_SYSTEM_PROMPT', '借款审核指令', 'audit', 'prompt', '你是一个借款审核助手。\n\n审核维度：\n1. 借款人信用等级是否达标\n2. 借款金额是否在授信额度内\n3. 借款用途是否合理合规\n4. 期限与还款能力是否匹配\n\n结论：通过/需复核/拒绝', 0.2, 1024, 0.8, 'glm-4', 0, 1, '借款审核'),
('SUMMARY_SYSTEM_PROMPT', '对话摘要指令', 'summary', 'prompt', '你是一个对话摘要助手。\n\n要求：\n1. 提取用户的核心问题和诉求\n2. 总结客服给出的解决方案\n3. 标注是否需要后续跟进\n4. 摘要不超过200字', 0.5, 512, 0.9, 'gpt-4', 0, 0, '对话摘要(暂未启用)');

-- 场景绑定
INSERT INTO `ai_chat_model_binding` (`id`, `scene`, `scene_name`, `model_id`, `model_name`, `prompt_id`, `prompt_key`, `kb_id`, `kb_name`, `enabled`, `remark`) VALUES
(1, 'intent', '意图识别', 1, 'GPT-4 对话模型', 2, 'INTENT_SYSTEM_PROMPT', NULL, '—', 1, '用户消息进来后先做意图识别'),
(2, 'qa', '业务问答', 1, 'GPT-4 对话模型', 1, 'CS_SYSTEM_PROMPT', 1, '金融风控知识库', 1, '基于知识库检索回答业务问题'),
(3, 'chitchat', '闲聊', 1, 'GPT-4 对话模型', 5, 'csChitchatClient', NULL, '—', 1, '处理非业务闲聊'),
(4, 'summary', '对话摘要', 1, 'GPT-4 对话模型', 8, 'SUMMARY_SYSTEM_PROMPT', NULL, '—', 0, '会话结束后生成摘要'),
(5, 'safety', '安全检测', 1, 'GPT-4 对话模型', 3, 'UNSAFE_KEYWORDS', NULL, '—', 1, '关键词命中拦截');