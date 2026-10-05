-- ============================================================================
-- AI 配置数据对齐脚本
-- 目的：把管理端三张 AI 配置表（模型配置 / Prompt 设置 / 对话模型选择）的种子数据
--       对齐到 finance-ai 的真实运行态，并修复历史遗留的中文乱码（0x3F）。
--
-- 真实运行态（代码依据）：
--   * 模型：application.yml -> spring.ai.openai.chat.options.model = qwen3.8-max
--                              spring.ai.openai.embedding.options.model = qwen3.7-text-embedding-flash
--   * 处理环节：ReActService.resolveSceneModel() 实际取用 scene = intent / chat / rag
--   * Prompt 场景：ReActService 实际取用 scene = intent / chat / rag（与绑定场景一一对应）
--   * 绑定优先级：ai_chat_model_binding 中对应场景的「模型 + 提示词」优先，
--                 未绑定/未启用才回退到 prompt_config 的场景默认指令
--   * 模型约束：「对话模型选择」只允许绑定 model_type='chat' 且 test_status=1（测试通过）的模型
--   * 关键词表：ContentGuard 在对话入口（/ai/chat、/ai/chat-stream、/ai/chat-sync）读取
--               UNSAFE_KEYWORDS / HANDOFF_KEYWORDS，命中即拦截（违规→合规话术；转人工词→转接提示），
--               不再调用大模型。切分规则见 KeywordUtils（逗号/顿号/分号/换行均可）
--   * 知识库：仅 knowledge_base.id = 6「使用手册」为有效数据（18 切片）
--
-- 执行：mysql -uroot -p ww_finance --default-character-set=utf8mb4 < sql/fix_ai_config.sql
-- 幂等：可重复执行。
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0. 表结构：ai_model_config 增加连通性测试结果
--    目的：让「对话模型选择」只允许选到「测试通过」的模型（管理端可验证的硬约束）。
--    MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断后动态执行，保证幂等。
-- ---------------------------------------------------------------------------
SET @has_test_status := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_model_config' AND COLUMN_NAME = 'test_status'
);
SET @ddl := IF(@has_test_status = 0,
    'ALTER TABLE ai_model_config
        ADD COLUMN test_status TINYINT NOT NULL DEFAULT 0 COMMENT ''连通性测试: 0-未测试/未通过 1-已通过'',
        ADD COLUMN test_time DATETIME NULL COMMENT ''最近一次测试时间'',
        ADD COLUMN test_message VARCHAR(500) DEFAULT '''' COMMENT ''最近一次测试结果说明''',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 1. ai_model_config：修正乱码名称，补录真实在用的对话/嵌入模型
-- ---------------------------------------------------------------------------
UPDATE ai_model_config SET name = 'GPT-4 对话模型'      WHERE id = 1;
UPDATE ai_model_config SET name = 'Ada 文本嵌入模型'    WHERE id = 2;
UPDATE ai_model_config SET name = 'GLM-4 对话模型'      WHERE id = 3;
UPDATE ai_model_config SET name = 'M3E 文本嵌入模型'    WHERE id = 4;
UPDATE ai_model_config SET name = 'BGE 重排模型'        WHERE id = 5;
UPDATE ai_model_config SET name = 'Qwen-Turbo 对话模型' WHERE id = 6;
UPDATE ai_model_config SET name = 'Qwen-Plus 对话模型'  WHERE id = 7;

-- GPT-4 不再作为默认（平台实际走 MaaS 的 qwen3.8-max）
UPDATE ai_model_config
SET is_default = 0, status = 0,
    remark = '历史种子数据，平台未接入，勿启用'
WHERE id = 1;

-- 补录真实在用的对话模型（id 交给自增；按 model_name + model_type 去重）
INSERT INTO ai_model_config
    (name, provider, model_type, model_name, api_url, api_key, max_tokens, temperature,
     is_default, status, remark)
SELECT 'Qwen3.8-Max 对话模型', 'maas', 'chat', 'qwen3.8-max',
       'https://maas.qianwenaiapi.com/compatible-mode/v1/chat/completions', '', 8192, 0.7,
       1, 1, 'MaaS 平台 OpenAI 兼容模式，当前智能客服实际使用的对话模型'
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT id FROM ai_model_config
                   WHERE model_name = 'qwen3.8-max' AND model_type = 'chat') t
);

-- 补录真实在用的嵌入模型（RAG 向量化，维度 1024）
INSERT INTO ai_model_config
    (name, provider, model_type, model_name, api_url, api_key, max_tokens, temperature,
     is_default, status, remark)
SELECT 'Qwen3.7 文本嵌入模型', 'maas', 'embedding', 'qwen3.7-text-embedding-flash',
       'https://maas.qianwenaiapi.com/compatible-mode/v1/embeddings', '', 8192, 0,
       1, 1, 'RAG 向量化使用的嵌入模型，输出维度 1024'
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT id FROM ai_model_config
                   WHERE model_name = 'qwen3.7-text-embedding-flash' AND model_type = 'embedding') t
);

-- Ada 嵌入模型已被 qwen3.7-text-embedding-flash 取代
UPDATE ai_model_config SET is_default = 0, status = 0 WHERE id = 2;

-- 重排模型未接入运行时
UPDATE ai_model_config
SET is_default = 0, status = 0, remark = '当前未接入重排流程'
WHERE id = 5;

-- ---------------------------------------------------------------------------
-- 2. prompt_config：修正乱码、补齐运行时真正读取的场景（intent / rag）
-- ---------------------------------------------------------------------------
-- 2.1 INTENT_SYSTEM_PROMPT：原场景为 chat 且被软删除，运行时 getConfigByScene("intent") 取不到
UPDATE prompt_config
SET name            = '意图路由提示词',
    scene           = 'intent',
    content_type    = 'prompt',
    model           = 'qwen3.8-max',
    temperature     = 0.0,
    max_tokens      = 256,
    is_default      = 1,
    status          = 1,
    is_deleted      = 0,
    remark          = 'Phase 1 意图路由：判断问题类型并决定是否调用知识库/时间工具',
    system_prompt   = '你是金融客服的意图路由器，根据用户问题判断是否需要调用工具。\n\n可用工具：\n- searchKnowledgeBase：检索知识库（借款利率、还款方式、贷款政策、产品规则、平台功能、文档资料等专业知识）\n- getCurrentDateTime：查询当前日期时间\n\n判断规则：\n1. 问候、闲聊、自我介绍、能力询问 → 不调用任何工具，直接回答\n2. 涉及借款/投资/账户等业务知识或平台资料 → 调用 searchKnowledgeBase\n3. 询问时间/日期 → 调用 getCurrentDateTime\n4. 同一轮对话不重复调用同一个工具\n\n只输出判断结论或工具调用，不要编造知识库里没有的信息。'
WHERE prompt_key = 'INTENT_SYSTEM_PROMPT';

-- 2.2 新增 RAG_SYSTEM_PROMPT：运行时 getConfigByScene("rag") 需要该场景
INSERT INTO prompt_config
    (prompt_key, name, scene, content_type, system_prompt, temperature, max_tokens, top_p,
     model, is_default, status, remark)
VALUES
    ('RAG_SYSTEM_PROMPT', '知识库问答提示词', 'rag', 'prompt',
     '你是知识库问答专家。请严格依据提供的参考资料回答用户问题：\n- 只使用参考资料中的信息，不编造资料中没有的内容\n- 若参考资料无法回答该问题，明确告知用户并建议转人工客服\n- 回答条理清晰、简洁准确，必要时分点说明',
     0.3, 2048, 0.9, 'qwen3.8-max', 1, 1,
     'Phase 2 知识库问答：检索命中后由该指令生成最终回答')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), scene = VALUES(scene), content_type = VALUES(content_type),
    system_prompt = VALUES(system_prompt), temperature = VALUES(temperature),
    max_tokens = VALUES(max_tokens), top_p = VALUES(top_p), model = VALUES(model),
    is_default = VALUES(is_default), status = VALUES(status), is_deleted = 0,
    remark = VALUES(remark);

-- 2.3 客服系统指令：确保场景/默认/模型与运行态一致
UPDATE prompt_config
SET scene = 'chat', content_type = 'prompt', model = 'qwen3.8-max',
    is_default = 1, status = 1, is_deleted = 0
WHERE prompt_key = 'CS_SYSTEM_PROMPT';

-- 2.4 其余历史条目：修正乱码名称，维持软删除（未接入运行时）
UPDATE prompt_config SET name = '闲聊应答提示词',   scene = 'chat',    is_deleted = 1 WHERE id = 5;
UPDATE prompt_config SET name = '风控审核提示词',   scene = 'risk',    is_deleted = 1 WHERE id = 6;
UPDATE prompt_config SET name = '借款审核提示词',   scene = 'audit',   is_deleted = 1 WHERE id = 7;
UPDATE prompt_config SET name = '会话摘要提示词',   scene = 'summary', is_deleted = 1 WHERE id = 8;

-- 2.5 恢复 id 5~8 的中文正文（原数据为 0x3F 乱码，仅剩名称可用）。
--     这些指令当前未被任何代码读取（软删除 + 未接入运行时），但保留可读正文，
--     便于日后真正接入风控/审核/摘要环节时直接启用，而不是面对一堆 "?????"。
UPDATE prompt_config
SET system_prompt = '你是旺旺信贷智能客服的闲聊应答助手。当用户只是问候、寒暄或询问你的能力时，用友好、简短、口语化的中文回应，不要长篇大论，也不要编造业务政策。'
WHERE id = 5 AND (system_prompt IS NULL OR system_prompt LIKE '%?%');

UPDATE prompt_config
SET system_prompt = '你是信贷业务风控审核助手。请依据用户提交的资料与交易信息，判断是否存在欺诈、套现、洗钱、多头借贷等风险，并给出风险等级（低/中/高）与可核查的依据。结论必须可追溯，不要臆测。'
WHERE id = 6 AND (system_prompt IS NULL OR system_prompt LIKE '%?%');

UPDATE prompt_config
SET system_prompt = '你是借款申请审核助手。请对照平台准入规则审核借款申请：核验身份与资料完整性、评估还款能力与征信表现、识别异常申请特征，并给出「通过/人工复核/拒绝」的建议及理由。'
WHERE id = 7 AND (system_prompt IS NULL OR system_prompt LIKE '%?%');

UPDATE prompt_config
SET system_prompt = '你是会话摘要助手。请把给定的客服会话压缩成结构化摘要，包含：用户核心诉求、已确认的关键事实（金额/期限/产品）、当前处理结论、待办事项。只保留事实，不要加入主观评价。'
WHERE id = 8 AND (system_prompt IS NULL OR system_prompt LIKE '%?%');

-- 2.6 两张关键词表：运行时已接入（finance-ai 的 ContentGuard 在调用大模型前做确定性匹配）。
--     注意：关键词表不参与场景指令选择，历史的 is_default=1 会让人误以为它也是"场景默认指令"，
--     这里统一置 0，并确保启用、未删除、content_type 正确。
UPDATE prompt_config
SET content_type = 'keywords', is_default = 0, status = 1, is_deleted = 0, model = '-'
WHERE prompt_key IN ('UNSAFE_KEYWORDS', 'HANDOFF_KEYWORDS');

UPDATE prompt_config
SET scene  = 'risk',
    remark = '命中即在调用大模型前拦截，回复合规话术（支持逗号/顿号/换行分隔；清空即关闭拦截）'
WHERE prompt_key = 'UNSAFE_KEYWORDS';

UPDATE prompt_config
SET scene  = 'chat',
    remark = '命中即在调用大模型前转人工，回复转接提示（支持逗号/顿号/换行分隔；清空即关闭转人工）'
WHERE prompt_key = 'HANDOFF_KEYWORDS';

-- 2.6.1 词表为空时运行时不会命中任何词（拦截/转人工静默失效），补回种子词表
UPDATE prompt_config
SET system_prompt = '暴力,色情,赌博,毒品,诈骗,洗钱,自杀,枪支,爆炸物,恐怖活动,传销,高利贷,套现,黑客攻击'
WHERE prompt_key = 'UNSAFE_KEYWORDS' AND (system_prompt IS NULL OR TRIM(system_prompt) = '');

UPDATE prompt_config
SET system_prompt = '人工,人工客服,转人工,真人客服,投诉,举报,找客服,人工服务,在线客服,客服电话,退款,维权,协商,投诉到底'
WHERE prompt_key = 'HANDOFF_KEYWORDS' AND (system_prompt IS NULL OR TRIM(system_prompt) = '');

-- 2.6.2 缺行时补录（全新库场景）
INSERT INTO prompt_config
    (prompt_key, name, scene, content_type, system_prompt, temperature, max_tokens, top_p,
     model, is_default, status, remark)
SELECT 'UNSAFE_KEYWORDS', '风控违规关键词', 'risk', 'keywords',
       '暴力,色情,赌博,毒品,诈骗,洗钱,自杀,枪支,爆炸物,恐怖活动,传销,高利贷,套现,黑客攻击',
       0, 0, 0, '-', 0, 1,
       '命中即在调用大模型前拦截，回复合规话术（支持逗号/顿号/换行分隔）'
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT id FROM prompt_config WHERE prompt_key = 'UNSAFE_KEYWORDS') t
);

INSERT INTO prompt_config
    (prompt_key, name, scene, content_type, system_prompt, temperature, max_tokens, top_p,
     model, is_default, status, remark)
SELECT 'HANDOFF_KEYWORDS', '转人工触发关键词', 'chat', 'keywords',
       '人工,人工客服,转人工,真人客服,投诉,举报,找客服,人工服务,在线客服,客服电话,退款,维权,协商,投诉到底',
       0, 0, 0, '-', 0, 1,
       '命中即在调用大模型前转人工，回复转接提示（支持逗号/顿号/换行分隔）'
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT id FROM prompt_config WHERE prompt_key = 'HANDOFF_KEYWORDS') t
);

-- ---------------------------------------------------------------------------
-- 3. ai_chat_model_binding：场景与运行时一一对应 —— intent / chat / rag
--
-- 为什么是一个场景一行：运行时的可配置槽位是「每个对话场景 = 提示词 + 模型 (+知识库)」。
--   intent 意图路由   —— Phase 1，读过 prompt_config.scene='intent'
--   chat   闲聊应答   —— Phase 2 未检索分支，读 prompt_config.scene='chat'
--   rag    知识库问答 —— Phase 2 检索命中分支，读 prompt_config.scene='rag'
-- 历史版本只有 intent / answer（answer 对应 Phase 2 的模型），既与提示词场景对不上，
-- 也无法为闲聊与 RAG 分别指定模型；本节统一拆成三行。
-- ---------------------------------------------------------------------------
-- 3.1 意图路由（Phase 1）—— 沿用原 intent 行
UPDATE ai_chat_model_binding
SET scene      = 'intent',
    scene_name = '意图路由',
    model_id   = (SELECT id FROM ai_model_config
                  WHERE model_name = 'qwen3.8-max' AND model_type = 'chat' AND is_deleted = 0 LIMIT 1),
    model_name = 'qwen3.8-max',
    prompt_id  = (SELECT id FROM prompt_config WHERE prompt_key = 'INTENT_SYSTEM_PROMPT' LIMIT 1),
    prompt_key = 'INTENT_SYSTEM_PROMPT',
    kb_id      = NULL,
    kb_name    = '—',
    enabled    = 1,
    is_deleted = 0,
    remark     = 'Phase 1：判断问题类型并决定是否调用知识库/时间工具'
WHERE scene = 'intent';

-- 3.2 原始种子里的 qa 行 → chat（只在还没有 chat 行时执行，避免撞 uk_scene）
UPDATE ai_chat_model_binding
SET scene      = 'chat',
    scene_name = '闲聊应答',
    prompt_id  = (SELECT id FROM prompt_config WHERE prompt_key = 'CS_SYSTEM_PROMPT' LIMIT 1),
    prompt_key = 'CS_SYSTEM_PROMPT',
    kb_id      = NULL,
    kb_name    = '—',
    enabled    = 1,
    is_deleted = 0,
    remark     = 'Phase 2 闲聊分支：不检索知识库，直接润色回答'
WHERE scene = 'qa'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM ai_chat_model_binding WHERE scene = 'chat') t);

-- 3.2.1 历史 answer 行（上一版脚本的产物）→ 拆成 chat + rag，然后归档 answer。
--       仅当 answer 行仍启用/未删除时执行；执行完会软删除它，因此重复执行不会覆盖管理员的后续修改。
-- chat 行：复用 answer 的模型，但闲聊分支不检索知识库
UPDATE ai_chat_model_binding b
JOIN ai_chat_model_binding a ON a.scene = 'answer' AND a.is_deleted = 0
SET b.scene_name = '闲聊应答',
    b.model_id   = a.model_id,
    b.model_name = a.model_name,
    b.prompt_id  = (SELECT id FROM prompt_config WHERE prompt_key = 'CS_SYSTEM_PROMPT' LIMIT 1),
    b.prompt_key = 'CS_SYSTEM_PROMPT',
    b.kb_id      = NULL,
    b.kb_name    = '—',
    b.enabled    = 1,
    b.is_deleted = 0,
    b.remark     = 'Phase 2 闲聊分支：不检索知识库，直接润色回答'
WHERE b.scene = 'chat';

-- rag 行：复用 answer 的模型与知识库
UPDATE ai_chat_model_binding b
JOIN ai_chat_model_binding a ON a.scene = 'answer' AND a.is_deleted = 0
SET b.scene_name = '知识库问答',
    b.model_id   = a.model_id,
    b.model_name = a.model_name,
    b.prompt_id  = (SELECT id FROM prompt_config WHERE prompt_key = 'RAG_SYSTEM_PROMPT' LIMIT 1),
    b.prompt_key = 'RAG_SYSTEM_PROMPT',
    b.kb_id      = a.kb_id,
    b.kb_name    = a.kb_name,
    b.enabled    = 1,
    b.is_deleted = 0,
    b.remark     = 'Phase 2 知识库问答分支：检索命中后依据参考资料回答'
WHERE b.scene = 'rag';

-- 3.2.2 若 chat / rag 行根本不存在（全新库），兜底插入
INSERT INTO ai_chat_model_binding
    (scene, scene_name, model_id, model_name, prompt_id, prompt_key, kb_id, kb_name, enabled, is_deleted, remark)
SELECT 'chat', '闲聊应答',
       (SELECT id FROM ai_model_config WHERE model_type = 'chat' AND status = 1 AND is_deleted = 0 ORDER BY is_default DESC, id LIMIT 1),
       (SELECT model_name FROM ai_model_config WHERE model_type = 'chat' AND status = 1 AND is_deleted = 0 ORDER BY is_default DESC, id LIMIT 1),
       (SELECT id FROM prompt_config WHERE prompt_key = 'CS_SYSTEM_PROMPT' LIMIT 1),
       'CS_SYSTEM_PROMPT', NULL, '—', 1, 0,
       'Phase 2 闲聊分支：不检索知识库，直接润色回答'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM ai_chat_model_binding WHERE scene = 'chat') t);

INSERT INTO ai_chat_model_binding
    (scene, scene_name, model_id, model_name, prompt_id, prompt_key, kb_id, kb_name, enabled, is_deleted, remark)
SELECT 'rag', '知识库问答',
       (SELECT id FROM ai_model_config WHERE model_type = 'chat' AND status = 1 AND is_deleted = 0 ORDER BY is_default DESC, id LIMIT 1),
       (SELECT model_name FROM ai_model_config WHERE model_type = 'chat' AND status = 1 AND is_deleted = 0 ORDER BY is_default DESC, id LIMIT 1),
       (SELECT id FROM prompt_config WHERE prompt_key = 'RAG_SYSTEM_PROMPT' LIMIT 1),
       'RAG_SYSTEM_PROMPT',
       (SELECT id FROM knowledge_base WHERE is_deleted = 0 ORDER BY id LIMIT 1),
       (SELECT name FROM knowledge_base WHERE is_deleted = 0 ORDER BY id LIMIT 1), 1, 0,
       'Phase 2 知识库问答分支：检索命中后依据参考资料回答'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM ai_chat_model_binding WHERE scene = 'rag') t);

-- 3.2.3 归档 answer 行：已拆成 chat / rag，不再被运行时读取
UPDATE ai_chat_model_binding
SET is_deleted = 1,
    remark     = '已拆分为 chat / rag 两个场景，保留仅供追溯'
WHERE scene = 'answer' AND is_deleted = 0;

-- 3.3 未接入运行时的历史场景 → 软删除（保留记录，便于追溯）
UPDATE ai_chat_model_binding
SET is_deleted = 1,
    remark     = '未接入 ReActService，保留仅供追溯'
WHERE scene IN ('chitchat', 'summary', 'safety');

-- 3.3.1 归档行同样修正乱码展示字段
UPDATE ai_chat_model_binding SET scene_name = '闲聊应答', kb_name = '—' WHERE scene = 'chitchat';
UPDATE ai_chat_model_binding SET scene_name = '会话摘要', kb_name = '—' WHERE scene = 'summary';
UPDATE ai_chat_model_binding SET scene_name = '风控审核', kb_name = '—' WHERE scene = 'safety';
UPDATE ai_chat_model_binding
SET model_id   = (SELECT id FROM ai_model_config
                  WHERE model_name = 'qwen3.8-max' AND model_type = 'chat' AND is_deleted = 0 LIMIT 1),
    model_name = 'qwen3.8-max'
WHERE scene IN ('chitchat', 'summary', 'safety');

-- 3.3.2 归档行里把「关键词表」当提示词引用属于语义错误（关键词表不是系统指令，不参与场景指令选择），
--       改指到语义正确的风控指令（prompt_id 为 NOT NULL，不能置空），
--       避免日后误判"关键词表被绑定占用"
UPDATE ai_chat_model_binding
SET prompt_id  = COALESCE(
        (SELECT id FROM prompt_config WHERE prompt_key = 'RISK_SYSTEM_PROMPT' LIMIT 1),
        (SELECT id FROM prompt_config WHERE prompt_key = 'CS_SYSTEM_PROMPT' LIMIT 1)),
    prompt_key = COALESCE(
        (SELECT prompt_key FROM prompt_config WHERE prompt_key = 'RISK_SYSTEM_PROMPT' LIMIT 1),
        'CS_SYSTEM_PROMPT')
WHERE is_deleted = 1 AND prompt_key IN ('UNSAFE_KEYWORDS', 'HANDOFF_KEYWORDS');

-- 3.4 若 3.1 的源行不存在（全新库），兜底插入 intent
INSERT INTO ai_chat_model_binding
    (scene, scene_name, model_id, model_name, prompt_id, prompt_key, kb_id, kb_name, enabled, remark)
SELECT 'intent', '意图路由',
       (SELECT id FROM ai_model_config WHERE model_name = 'qwen3.8-max' AND model_type = 'chat' AND is_deleted = 0 LIMIT 1),
       'qwen3.8-max',
       (SELECT id FROM prompt_config WHERE prompt_key = 'INTENT_SYSTEM_PROMPT' LIMIT 1),
       'INTENT_SYSTEM_PROMPT', NULL, '—', 1,
       'Phase 1：判断问题类型并决定是否调用知识库/时间工具'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM ai_chat_model_binding WHERE scene = 'intent') t);

-- 3.5 双保险：确保三个运行场景最终处于启用状态
UPDATE ai_chat_model_binding SET enabled = 1, is_deleted = 0 WHERE scene IN ('intent', 'chat', 'rag');

-- ---------------------------------------------------------------------------
-- 4. knowledge_base：修正历史乱码名称（有效库 id=6 不动）
-- ---------------------------------------------------------------------------
UPDATE knowledge_base SET name = '产品知识库',   description = '借款/投资/账户等金融业务知识，已下线' WHERE id = 1;
UPDATE knowledge_base SET name = '投资产品库',   description = '投资类产品资料，已下线'               WHERE id = 3;

-- ---------------------------------------------------------------------------
-- 5. 校验
-- ---------------------------------------------------------------------------
SELECT '--- ai_model_config ---' AS section;
SELECT id, name, provider, model_type, model_name, is_default, status, is_deleted,
       test_status, test_time, LEFT(test_message, 40) AS test_head
FROM ai_model_config ORDER BY id;
SELECT '--- 可被「对话模型选择」绑定的模型（chat + 已测试通过 + 启用）---' AS section;
SELECT id, name, model_name, test_status FROM ai_model_config
WHERE model_type = 'chat' AND status = 1 AND is_deleted = 0 AND test_status = 1 ORDER BY id;
SELECT '--- prompt_config ---' AS section;
SELECT id, prompt_key, name, scene, content_type, model, is_default, status, is_deleted
FROM prompt_config ORDER BY id;
SELECT '--- 运行时生效的场景指令（应与 ReActService 读取的一致）---' AS section;
SELECT scene, prompt_key, name, is_default
FROM prompt_config
WHERE is_deleted = 0 AND status = 1 AND (content_type IS NULL OR content_type <> 'keywords')
ORDER BY scene, is_default DESC, id;
SELECT '--- 关键词表（运行时拦截使用）---' AS section;
SELECT prompt_key, name, status,
       (LENGTH(system_prompt) - LENGTH(REPLACE(system_prompt, ',', '')) + 1) AS approx_token_count
FROM prompt_config WHERE content_type = 'keywords' AND is_deleted = 0;
SELECT '--- ai_chat_model_binding ---' AS section;
SELECT id, scene, scene_name, model_id, model_name, prompt_id, prompt_key, kb_id, kb_name, enabled, is_deleted
FROM ai_chat_model_binding ORDER BY id;
