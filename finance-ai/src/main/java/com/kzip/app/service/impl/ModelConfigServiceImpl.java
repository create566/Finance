package com.kzip.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kzip.app.entity.ChatModelBinding;
import com.kzip.app.entity.ModelConfig;
import com.kzip.app.mapper.ChatModelBindingMapper;
import com.kzip.app.mapper.ModelConfigMapper;
import com.kzip.app.service.ModelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ModelConfigServiceImpl extends ServiceImpl<ModelConfigMapper, ModelConfig> implements ModelConfigService {

    @Autowired
    private ChatModelBindingMapper chatModelBindingMapper;

    /** 应用自身配置的对话模型密钥与地址：仅当待测地址与它同源时才回退使用，避免把密钥发往第三方地址 */
    @Value("${spring.ai.openai.api-key:}")
    private String appApiKey;

    @Value("${spring.ai.openai.base-url:}")
    private String appBaseUrl;

    @Override
    public List<ModelConfig> listAll(ModelConfig query, String keyword) {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // 前端搜索框为"名称/模型标识"二合一，必须同时命中两列，否则搜索静默失效
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ModelConfig::getName, kw)
                    .or().like(ModelConfig::getModelName, kw));
        }
        if (query != null) {
            if (StringUtils.hasText(query.getName())) {
                wrapper.like(ModelConfig::getName, query.getName().trim());
            }
            if (StringUtils.hasText(query.getModelType())) {
                wrapper.eq(ModelConfig::getModelType, query.getModelType());
            }
            if (StringUtils.hasText(query.getProvider())) {
                wrapper.eq(ModelConfig::getProvider, query.getProvider());
            }
        }
        wrapper.orderByDesc(ModelConfig::getUpdateTime).orderByAsc(ModelConfig::getId);
        List<ModelConfig> list = this.list(wrapper);
        // 密钥不回传明文：列表只需要让管理员看到"配没配、尾号是多少"
        list.forEach(m -> m.setApiKey(maskApiKey(m.getApiKey())));
        fillUsage(list);
        return list;
    }

    /**
     * 回填每个模型被哪些对话场景引用。
     *
     * <p>删除模型被拒的原因就是"它正被「对话模型选择」用着"，但此前管理员点了才知道，
     * 而错误信息里的场景名又来得太晚。这里一次性查出来附在列表上，让管理端能提前展示与拦截。
     * 一次查询后分组，避免逐行查（列表很短，但没必要做成 N+1）。
     */
    private void fillUsage(List<ModelConfig> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<ChatModelBinding> all = chatModelBindingMapper.selectAllForAdmin();
        Map<Long, List<ChatModelBinding>> byModel = all.stream()
                .filter(b -> b.getModelId() != null)
                .collect(Collectors.groupingBy(ChatModelBinding::getModelId));
        for (ModelConfig m : list) {
            List<ChatModelBinding> refs = byModel.get(m.getId());
            if (refs == null || refs.isEmpty()) {
                m.setUsedBy("");
                m.setActiveUsedBy("");
                continue;
            }
            m.setUsedBy(sceneNames(refs));
            m.setActiveUsedBy(sceneNames(refs.stream()
                    .filter(b -> b.getEnabled() != null && b.getEnabled() == 1)
                    .toList()));
        }
    }

    @Override
    @Transactional
    public boolean saveWithCheck(ModelConfig config) {
        if (!StringUtils.hasText(config.getName())) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        if (!StringUtils.hasText(config.getModelType())) {
            throw new IllegalArgumentException("模型类型不能为空");
        }
        if (!StringUtils.hasText(config.getModelName())) {
            throw new IllegalArgumentException("模型标识不能为空");
        }
        if (needApiUrl(config.getModelType()) && !StringUtils.hasText(config.getApiUrl())) {
            throw new IllegalArgumentException("对话/嵌入模型必须配置 API 地址");
        }
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            this.baseMapper.clearDefaultByType(config.getModelType());
        }
        config.setStatus(config.getStatus() != null ? config.getStatus() : 1);
        config.setId(null);
        config.setCreateTime(null);
        config.setUpdateTime(null);
        // 新建的模型从未测过：默认不可被「对话模型选择」绑定，必须先点「测试」通过
        config.setTestStatus(0);
        config.setTestTime(null);
        config.setTestMessage("");
        return this.save(config);
    }

    @Override
    @Transactional
    public boolean updateWithCheck(ModelConfig config) {
        if (config.getId() == null) {
            throw new IllegalArgumentException("ID不能为空");
        }
        ModelConfig exist = this.getById(config.getId());
        if (exist == null) {
            throw new IllegalArgumentException("模型配置不存在");
        }

        List<ChatModelBinding> refs = chatModelBindingMapper.selectByModelId(config.getId());
        boolean typeChanged = StringUtils.hasText(config.getModelType())
                && !config.getModelType().equalsIgnoreCase(exist.getModelType());
        if (typeChanged && !refs.isEmpty()) {
            throw new IllegalArgumentException("该模型已被「对话模型选择」的【" + sceneNames(refs)
                    + "】引用，不允许修改模型类型；请先解除绑定");
        }
        if (config.getStatus() != null && config.getStatus() == 0
                && exist.getStatus() != null && exist.getStatus() == 1) {
            assertNoActiveBinding(config.getId(), "停用");
        }

        // 前端拿到的是脱敏后的密钥，原样回传说明"没改"，此时必须沿用库里的真值，否则会把密钥覆盖成 ****xxxx
        if (!StringUtils.hasText(config.getApiKey())
                || config.getApiKey().equals(maskApiKey(exist.getApiKey()))) {
            config.setApiKey(exist.getApiKey());
        }
        if (needApiUrl(config.getModelType()) && !StringUtils.hasText(config.getApiUrl())) {
            throw new IllegalArgumentException("对话/嵌入模型必须配置 API 地址");
        }
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            this.baseMapper.clearDefaultByType(config.getModelType());
        }

        // 模型标识是运行时真正发给大模型的字段，改名后必须同步绑定的冗余列，否则线上仍请求旧模型
        String newModelName = StringUtils.hasText(config.getModelName())
                ? config.getModelName() : exist.getModelName();
        boolean nameChanged = !newModelName.equals(exist.getModelName());

        // 影响连接结果的字段只要变了，既有"测试通过"结论立即失效，
        // 否则会出现"改了地址/密钥仍显示测试通过"，绕过「只允许绑定测试通过的模型」这条约束
        boolean connectionChanged = nameChanged
                || !equalsIgnoreCaseSafe(config.getModelType(), exist.getModelType())
                || !equalsSafe(config.getApiUrl(), exist.getApiUrl())
                || !equalsSafe(config.getApiKey(), exist.getApiKey());

        config.setCreateTime(null);
        config.setUpdateTime(null);
        // 测试结果是系统自动写入的，不接受前端回传，避免被表单值覆盖
        config.setTestStatus(null);
        config.setTestTime(null);
        config.setTestMessage(null);

        boolean ok = this.updateById(config);
        if (ok && nameChanged) {
            int synced = chatModelBindingMapper.syncModelName(config.getId(), newModelName);
            if (synced > 0) {
                log.info("模型标识变更，已同步 {} 条场景绑定: id={}, {} -> {}",
                        synced, config.getId(), exist.getModelName(), newModelName);
            }
        }
        if (ok && connectionChanged) {
            this.baseMapper.invalidateTestResult(config.getId());
            log.info("模型连接参数变更，测试结论已失效: id={}", config.getId());
        }
        return ok;
    }

    @Override
    @Transactional
    public boolean removeConfig(Long id) {
        ModelConfig exist = this.getById(id);
        if (exist == null) {
            throw new IllegalArgumentException("模型配置不存在");
        }
        List<ChatModelBinding> refs = chatModelBindingMapper.selectByModelId(id);
        if (!refs.isEmpty()) {
            throw new IllegalArgumentException("该模型已被「对话模型选择」的【" + sceneNames(refs)
                    + "】引用，请先解除绑定或改绑其它模型后再删除");
        }
        return this.removeById(id);
    }

    @Override
    public Map<String, Object> testConnect(Long id) {
        Map<String, Object> result = new HashMap<>();
        ModelConfig config = this.getById(id);
        if (config == null) {
            result.put("success", false);
            result.put("latency", 0);
            result.put("message", "模型配置不存在");
            return result;
        }
        if (!StringUtils.hasText(config.getApiUrl())) {
            return persistTestResult(id, result, false, 0, "API地址未配置");
        }

        long start = System.currentTimeMillis();
        int code = 0;
        String detail = "";
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(config.getApiUrl()).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);

            // 对话/嵌入模型发一次最小真实请求：既验证地址与密钥，也验证模型名是否被服务端接受。
            // 原先统一用 HEAD 探测，网关对 HEAD 返回 405 被算作"连接成功"，等于什么都没验。
            boolean chatLike = "chat".equalsIgnoreCase(config.getModelType())
                    || "embedding".equalsIgnoreCase(config.getModelType());
            if (chatLike) {
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                String key = resolveApiKey(config);
                if (StringUtils.hasText(key)) {
                    conn.setRequestProperty("Authorization", "Bearer " + key);
                }
                String body = "chat".equalsIgnoreCase(config.getModelType())
                        ? "{\"model\":\"" + escapeJson(config.getModelName())
                        + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1}"
                        : "{\"model\":\"" + escapeJson(config.getModelName()) + "\",\"input\":\"ping\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                conn.setRequestMethod("HEAD");
            }

            code = conn.getResponseCode();
            detail = readBody(conn, code);
            conn.disconnect();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            result.put("httpStatus", code);
            return persistTestResult(id, result, false, latency, msg);
        }

        long latency = System.currentTimeMillis() - start;
        result.put("httpStatus", code);
        if (code >= 200 && code < 300) {
            return persistTestResult(id, result, true, latency, "连接成功，模型可用");
        }
        return persistTestResult(id, result, false, latency,
                "HTTP " + code + (detail.isEmpty() ? "" : "：" + detail));
    }

    /**
     * 组装测试结果并落库。
     * 「对话模型选择」只允许绑定 test_status=1 的模型，因此结论必须持久化，
     * 否则刷新页面/换个人来看就"没测过"，约束形同虚设。
     */
    private Map<String, Object> persistTestResult(Long id, Map<String, Object> result,
                                                  boolean success, long latency, String message) {
        result.put("success", success);
        result.put("latency", latency);
        result.put("message", message);
        result.put("testStatus", success ? 1 : 0);
        if (id != null) {
            try {
                String stored = message.length() > 480 ? message.substring(0, 480) + "..." : message;
                this.baseMapper.markTestResult(id, success ? 1 : 0, stored);
            } catch (Exception e) {
                // 落库失败不应影响测试结果返回，但必须留下痕迹
                log.warn("测试结果落库失败: id={}, error={}", id, e.getMessage());
            }
        }
        log.info("模型连通性测试: id={}, success={}, latency={}ms, message={}", id, success, latency, message);
        return result;
    }

    private static boolean equalsSafe(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean equalsIgnoreCaseSafe(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    /**
     * 取用于真实探测的密钥：优先模型配置自身的；
     * 自身为空且地址与应用同源（同 base-url）时，回退到应用配置的密钥。
     * 同源限制是为了避免把平台密钥发往管理员随意填写的第三方地址。
     */
    private String resolveApiKey(ModelConfig config) {
        if (StringUtils.hasText(config.getApiKey())) {
            return config.getApiKey();
        }
        if (StringUtils.hasText(appApiKey) && StringUtils.hasText(appBaseUrl)
                && config.getApiUrl().startsWith(appBaseUrl)) {
            return appApiKey;
        }
        return null;
    }

    private static boolean needApiUrl(String modelType) {
        return "chat".equalsIgnoreCase(modelType) || "embedding".equalsIgnoreCase(modelType);
    }

    /** 密钥脱敏：保留末 4 位便于管理员核对，其余打码 */
    static String maskApiKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        String k = key.trim();
        return k.length() <= 4 ? "****" : "****" + k.substring(k.length() - 4);
    }

    private static String escapeJson(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 读取响应体（成功读 input，失败读 error），截断到 300 字，用于把服务端的真实原因告诉管理员 */
    private static String readBody(HttpURLConnection conn, int code) {
        try (InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream()) {
            if (in == null) {
                return "";
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
            return text.length() > 300 ? text.substring(0, 300) + "..." : text;
        } catch (Exception e) {
            return "";
        }
    }

    /** 存在"启用中"的绑定引用该模型时，禁止停用，避免运行态出现"绑定启用但模型已停用"的矛盾配置 */
    private void assertNoActiveBinding(Long modelId, String action) {
        List<ChatModelBinding> active = chatModelBindingMapper.selectByModelId(modelId).stream()
                .filter(b -> b.getEnabled() != null && b.getEnabled() == 1)
                .toList();
        if (!active.isEmpty()) {
            throw new IllegalArgumentException("该模型正在被「对话模型选择」的【" + sceneNames(active)
                    + "】使用，无法" + action + "；请先解除绑定或改绑其它模型");
        }
    }

    private static String sceneNames(List<ChatModelBinding> bindings) {
        return bindings.stream()
                .map(b -> StringUtils.hasText(b.getSceneName()) ? b.getSceneName() : b.getScene())
                .distinct()
                .collect(Collectors.joining("、"));
    }
}
