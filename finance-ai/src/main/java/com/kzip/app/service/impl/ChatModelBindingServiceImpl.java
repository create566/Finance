package com.kzip.app.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kzip.app.entity.ChatModelBinding;
import com.kzip.app.entity.KnowledgeBase;
import com.kzip.app.entity.ModelConfig;
import com.kzip.app.entity.PromptConfig;
import com.kzip.app.mapper.ChatModelBindingMapper;
import com.kzip.app.mapper.KnowledgeBaseMapper;
import com.kzip.app.mapper.ModelConfigMapper;
import com.kzip.app.mapper.PromptConfigMapper;
import com.kzip.app.service.ChatModelBindingService;
import com.kzip.app.service.PromptConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话模型绑定：把「对话场景(scene) → 模型 / 提示词 / 知识库」落到数据库。
 *
 * <p>scene 取值必须与运行时读取的场景字面量一一对应，否则配置不会生效：
 * <ul>
 *   <li>{@code intent} —— Phase 1 意图路由（判断是否调用知识库/时间工具），读 prompt_config.scene='intent'</li>
 *   <li>{@code chat}   —— Phase 2 闲聊分支（不检索知识库），读 prompt_config.scene='chat'</li>
 *   <li>{@code rag}    —— Phase 2 知识库问答分支（检索命中），读 prompt_config.scene='rag'</li>
 * </ul>
 *
 * <p>绑定的意义：运行时会<em>优先</em>取本表对应场景的「模型 + 提示词」；
 * 未绑定/未启用时才回退到 Prompt 设置里该场景的默认指令。
 * 也就是说这里是"覆盖层"，Prompt 设置是"默认值层"，两处对齐后就不存在"配了不生效"。
 */
@Slf4j
@Service
public class ChatModelBindingServiceImpl extends ServiceImpl<ChatModelBindingMapper, ChatModelBinding>
        implements ChatModelBindingService {

    /** 运行时可配置的对话场景，顺序即前端展示顺序；必须与 ReActService 读取的场景一致 */
    private static final Map<String, String> SUPPORTED_SCENES;

    static {
        Map<String, String> scenes = new LinkedHashMap<>();
        scenes.put("intent", "意图路由");
        scenes.put("chat", "闲聊应答");
        scenes.put("rag", "知识库问答");
        SUPPORTED_SCENES = Collections.unmodifiableMap(scenes);
    }

    private static final String NO_KB = "—";

    /** 绑定未启用时，回退读哪个场景的 Prompt 默认指令（与场景同名，保留映射便于以后扩展） */
    private static final Map<String, String> PROMPT_SCENE_FALLBACK;

    /** ReActService 中代码内置的兜底模型 */
    private static final String FALLBACK_MODEL = "qwen3.8-max";

    static {
        Map<String, String> fallback = new LinkedHashMap<>();
        fallback.put("intent", "intent");
        fallback.put("chat", "chat");
        fallback.put("rag", "rag");
        PROMPT_SCENE_FALLBACK = Collections.unmodifiableMap(fallback);
    }

    /** 对话场景只允许绑定对话模型，向量/重排模型不具备对话能力 */
    private static final String CHAT_MODEL_TYPE = "chat";

    @Autowired
    private ModelConfigMapper modelConfigMapper;

    @Autowired
    private PromptConfigMapper promptConfigMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private PromptConfigService promptConfigService;

    @Override
    public List<ChatModelBinding> listAll() {
        return this.baseMapper.selectAllForAdmin();
    }

    @Override
    public Map<String, String> supportedScenes() {
        return SUPPORTED_SCENES;
    }

    @Override
    public List<Map<String, Object>> effectiveConfigs() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : SUPPORTED_SCENES.entrySet()) {
            String scene = entry.getKey();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("scene", scene);
            item.put("sceneName", entry.getValue());

            ChatModelBinding binding = this.baseMapper.selectByScene(scene);
            boolean bound = binding != null
                    && binding.getEnabled() != null && binding.getEnabled() == 1
                    && StringUtils.hasText(binding.getModelName());

            if (bound) {
                item.put("bound", true);
                item.put("source", "binding");
                item.put("sourceText", "对话模型选择（已启用绑定）");
                item.put("modelId", binding.getModelId());
                item.put("modelName", binding.getModelName());
                item.put("promptKey", binding.getPromptKey());
                item.put("kbId", binding.getKbId());
                item.put("kbName", StringUtils.hasText(binding.getKbName()) ? binding.getKbName() : NO_KB);
                // 已绑定的模型也把测试状态带出去：老数据可能是在"测试门禁"之前绑定的，
                // 前端据此提示管理员补测，而不是等运行时报错
                ModelConfig boundModel = binding.getModelId() == null
                        ? null : modelConfigMapper.selectById(binding.getModelId());
                item.put("modelTestStatus", boundModel == null ? null : boundModel.getTestStatus());
                item.put("modelTestMessage", boundModel == null ? null : boundModel.getTestMessage());
            } else {
                String promptScene = PROMPT_SCENE_FALLBACK.getOrDefault(scene, scene);
                PromptConfig cfg = promptConfigService.getActiveByScene(promptScene);
                String model = cfg != null && StringUtils.hasText(cfg.getModel()) && !"-".equals(cfg.getModel())
                        ? cfg.getModel() : FALLBACK_MODEL;
                item.put("bound", false);
                item.put("source", cfg != null ? "prompt" : "default");
                item.put("sourceText", cfg != null
                        ? "Prompt 配置 · " + promptScene + " 场景"
                        : "代码内置默认值（未配置任何绑定与指令模型）");
                item.put("modelId", null);
                item.put("modelName", model);
                item.put("promptKey", cfg != null ? cfg.getPromptKey() : null);
                item.put("kbId", null);
                item.put("kbName", NO_KB);
                item.put("modelTestStatus", null);
                item.put("modelTestMessage", null);
            }
            list.add(item);
        }
        return list;
    }

    @Override
    @Transactional
    public boolean saveBinding(ChatModelBinding binding) {
        if (binding == null || !StringUtils.hasText(binding.getScene())) {
            throw new IllegalArgumentException("处理环节不能为空");
        }
        String scene = binding.getScene().trim();
        if (!SUPPORTED_SCENES.containsKey(scene)) {
            throw new IllegalArgumentException("不支持的处理环节：" + scene
                    + "，可选值 " + String.join("/", SUPPORTED_SCENES.keySet()));
        }
        if (binding.getModelId() == null) {
            throw new IllegalArgumentException("请选择使用模型");
        }
        binding.setScene(scene);
        binding.setSceneName(SUPPORTED_SCENES.get(scene));
        if (binding.getEnabled() == null) {
            binding.setEnabled(1);
        }
        resolveNames(binding);

        // scene 上有 UNIQUE KEY(uk_scene)，软删除行仍占用该键，因此先查再决定 insert / 复活
        ChatModelBinding exist = this.baseMapper.selectAnyByScene(scene);
        if (exist != null) {
            binding.setId(exist.getId());
            return this.baseMapper.applyBinding(binding) > 0;
        }
        return this.save(binding);
    }

    @Override
    @Transactional
    public boolean updateBinding(ChatModelBinding binding) {
        if (binding == null || binding.getId() == null) {
            throw new IllegalArgumentException("ID不能为空");
        }
        ChatModelBinding exist = this.getById(binding.getId());
        if (exist == null) {
            throw new IllegalArgumentException("绑定配置不存在");
        }
        // scene / scene_name 属于系统语义，不接受前端修改，直接沿用库中值
        binding.setScene(exist.getScene());
        binding.setSceneName(exist.getSceneName());
        if (binding.getModelId() == null) {
            binding.setModelId(exist.getModelId());
        }
        if (binding.getEnabled() == null) {
            binding.setEnabled(exist.getEnabled());
        }
        resolveNames(binding);
        return this.baseMapper.applyBinding(binding) > 0;
    }

    @Override
    @Transactional
    public boolean removeBinding(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID不能为空");
        }
        if (this.getById(id) == null) {
            throw new IllegalArgumentException("绑定配置不存在");
        }
        return this.removeById(id);
    }

    @Override
    @Transactional
    public boolean toggleEnabled(Long id) {
        ChatModelBinding exist = this.getById(id);
        if (exist == null) {
            throw new IllegalArgumentException("绑定配置不存在");
        }
        ChatModelBinding update = new ChatModelBinding();
        update.setId(exist.getId());
        update.setScene(exist.getScene());
        update.setSceneName(exist.getSceneName());
        update.setModelId(exist.getModelId());
        update.setModelName(exist.getModelName());
        update.setPromptId(exist.getPromptId());
        update.setPromptKey(exist.getPromptKey());
        update.setKbId(exist.getKbId());
        update.setKbName(exist.getKbName());
        update.setRemark(exist.getRemark());
        update.setEnabled(exist.getEnabled() != null && exist.getEnabled() == 1 ? 0 : 1);
        return this.baseMapper.applyBinding(update) > 0;
    }

    @Override
    public ChatModelBinding getByScene(String scene) {
        return this.baseMapper.selectByScene(scene);
    }

    /**
     * 由 ID 反查展示名称，避免信任前端回传的冗余字段（改名后不同步、或被伪造）。
     * model_name 直接取模型配置的 model_name —— 该值会被当作真实模型标识传给大模型接口。
     */
    private void resolveNames(ChatModelBinding binding) {
        ModelConfig model = modelConfigMapper.selectById(binding.getModelId());
        if (model == null) {
            throw new IllegalArgumentException("所选模型不存在或已删除");
        }
        if (model.getStatus() != null && model.getStatus() == 0) {
            throw new IllegalArgumentException("所选模型「" + model.getName() + "」已禁用，请先在模型配置中启用");
        }
        // 约束一：对话场景只能选对话模型。向量/重排模型没有对话接口，选进来必然运行时报错
        if (!CHAT_MODEL_TYPE.equalsIgnoreCase(model.getModelType())) {
            throw new IllegalArgumentException("「" + model.getName() + "」是"
                    + typeText(model.getModelType()) + "模型，不能用于对话场景；请选择对话（chat）模型");
        }
        // 约束二：必须是测通过的模型。"没测过"和"测失败"一样不可用，避免把不可达的模型配到线上
        if (model.getTestStatus() == null || model.getTestStatus() != 1) {
            throw new IllegalArgumentException("所选模型「" + model.getName()
                    + "」尚未通过连通性测试，请先到「模型配置」点击「测试」并通过后再绑定");
        }
        binding.setModelName(model.getModelName());

        if (binding.getPromptId() != null && binding.getPromptId() > 0) {
            PromptConfig prompt = promptConfigMapper.selectById(binding.getPromptId());
            if (prompt == null) {
                throw new IllegalArgumentException("所选 Prompt 指令不存在或已删除");
            }
            // 关键词表不是系统指令，绑上来会被当作 system prompt 发给模型，属于配置语义错误
            if (prompt.getContentType() != null
                    && "keywords".equalsIgnoreCase(prompt.getContentType().trim())) {
                throw new IllegalArgumentException("「" + prompt.getName()
                        + "」是关键词表，不能作为对话指令绑定；请选择系统指令");
            }
            if (prompt.getStatus() == null || prompt.getStatus() != 1) {
                throw new IllegalArgumentException("所选指令「" + prompt.getName()
                        + "」已停用，请先在 Prompt 设置中启用");
            }
            binding.setPromptKey(prompt.getPromptKey());
        } else {
            binding.setPromptId(0L);
            binding.setPromptKey("");
        }

        if (binding.getKbId() != null && binding.getKbId() > 0) {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(binding.getKbId());
            if (kb == null) {
                throw new IllegalArgumentException("所选知识库不存在或已删除");
            }
            binding.setKbName(kb.getName());
        } else {
            binding.setKbId(null);
            binding.setKbName(NO_KB);
        }
        if (!StringUtils.hasText(binding.getRemark())) {
            binding.setRemark("");
        }
    }

    /** 模型类型的中文名，用于错误提示（让管理员知道为什么选不了） */
    private static String typeText(String modelType) {
        if (modelType == null) {
            return "未知类型";
        }
        return switch (modelType.toLowerCase()) {
            case "chat" -> "对话";
            case "embedding" -> "向量/嵌入";
            case "rerank" -> "重排";
            default -> modelType;
        };
    }
}
