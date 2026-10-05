package com.kzip.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kzip.app.entity.ChatModelBinding;
import com.kzip.app.entity.PromptConfig;
import com.kzip.app.mapper.ChatModelBindingMapper;
import com.kzip.app.mapper.PromptConfigMapper;
import com.kzip.app.service.PromptConfigService;
import com.kzip.app.service.PromptSelector;
import com.kzip.app.service.PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PromptConfigServiceImpl extends ServiceImpl<PromptConfigMapper, PromptConfig> implements PromptConfigService {

    @Autowired
    private ChatModelBindingMapper chatModelBindingMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 通知 {@link PromptService} 刷新缓存。
     * 关键词表（UNSAFE_KEYWORDS / HANDOFF_KEYWORDS）被改动后，必须立刻生效，
     * 否则要等 5 分钟的定时刷新，管理员改了词表却感觉"没反应"。
     * 用事件而非直接注入，是为了避免 PromptService ↔ PromptConfigService 循环依赖。
     */
    private void notifyChanged(String reason) {
        eventPublisher.publishEvent(new PromptService.ChangedEvent(reason));
    }

    @Override
    public List<PromptConfig> listAll(PromptConfig query, String keyword) {
        LambdaQueryWrapper<PromptConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // 前端搜索框为"名称/标识"二合一，必须同时命中两列，否则搜索静默失效
            String kw = keyword.trim();
            wrapper.and(w -> w.like(PromptConfig::getName, kw)
                    .or().like(PromptConfig::getPromptKey, kw));
        }
        if (query != null) {
            if (StringUtils.hasText(query.getPromptKey())) {
                wrapper.like(PromptConfig::getPromptKey, query.getPromptKey().trim());
            }
            if (StringUtils.hasText(query.getName())) {
                wrapper.like(PromptConfig::getName, query.getName().trim());
            }
            if (StringUtils.hasText(query.getScene())) {
                wrapper.eq(PromptConfig::getScene, query.getScene());
            }
            if (StringUtils.hasText(query.getContentType())) {
                wrapper.eq(PromptConfig::getContentType, query.getContentType());
            }
        }
        // 排序即生效优先级顺序（PromptSelector 依赖），必须与 PromptService 刷新时拿到的一致
        wrapper.orderByDesc(PromptConfig::getUpdateTime).orderByAsc(PromptConfig::getId);
        List<PromptConfig> list = this.list(wrapper);
        markEffective(list);
        return list;
    }

    @Override
    public List<PromptConfig> listActiveByScene(String scene) {
        return this.baseMapper.selectActiveByScene(scene);
    }

    @Override
    public PromptConfig getActiveByScene(String scene) {
        List<PromptConfig> list = this.baseMapper.selectActiveByScene(scene);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    @Transactional
    public boolean saveWithCheck(PromptConfig config) {
        if (!StringUtils.hasText(config.getPromptKey())) {
            throw new IllegalArgumentException("指令标识不能为空");
        }
        if (!StringUtils.hasText(config.getName())) {
            throw new IllegalArgumentException("指令名称不能为空");
        }
        if (!StringUtils.hasText(config.getScene())) {
            throw new IllegalArgumentException("使用场景不能为空");
        }
        if (this.lambdaQuery().eq(PromptConfig::getPromptKey, config.getPromptKey()).count() > 0) {
            throw new IllegalArgumentException("指令标识已存在：" + config.getPromptKey());
        }
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            this.baseMapper.clearDefaultByScene(config.getScene());
        }
        config.setStatus(config.getStatus() != null ? config.getStatus() : 1);
        // 新增时 id 由自增分配，时间交给 MyMetaObjectHandler
        config.setId(null);
        config.setCreateTime(null);
        config.setUpdateTime(null);
        config.setEffective(null);
        boolean saved = this.save(config);
        if (saved) {
            notifyChanged("新增指令 " + config.getPromptKey());
        }
        return saved;
    }

    @Override
    @Transactional
    public boolean updateWithCheck(PromptConfig config) {
        if (config.getId() == null) {
            throw new IllegalArgumentException("ID不能为空");
        }
        PromptConfig exist = this.getById(config.getId());
        if (exist == null) {
            throw new IllegalArgumentException("提示词配置不存在");
        }
        // 正在被启用中的绑定使用时，不允许把指令停用（否则该环节会掉回代码内置的兜底提示词）
        if (config.getStatus() != null && config.getStatus() == 0
                && exist.getStatus() != null && exist.getStatus() == 1) {
            assertNoActiveBinding(config.getId(), "停用");
            assertRuntimeKeywordNotDisabled(exist.getPromptKey(), "停用");
        }
        if (config.getIsDefault() != null && config.getIsDefault() == 1 && StringUtils.hasText(config.getScene())) {
            this.baseMapper.clearDefaultByScene(config.getScene());
        }
        config.setEffective(null);
        // 前端会把列表里的 createTime/updateTime 一并回传，置空后由 MyMetaObjectHandler 重新写入 now()，
        // 否则"更新时间"会一直停在旧值
        config.setCreateTime(null);
        config.setUpdateTime(null);
        boolean updated = this.updateById(config);
        if (updated) {
            notifyChanged("更新指令 " + exist.getPromptKey());
        }
        return updated;
    }

    @Override
    @Transactional
    public boolean toggleStatus(Long id) {
        PromptConfig config = this.getById(id);
        if (config == null) {
            throw new IllegalArgumentException("提示词配置不存在");
        }
        boolean turningOff = config.getStatus() != null && config.getStatus() == 1;
        if (turningOff) {
            assertNoActiveBinding(id, "停用");
            assertRuntimeKeywordNotDisabled(config.getPromptKey(), "停用");
        }
        config.setStatus(turningOff ? 0 : 1);
        config.setEffective(null);
        config.setCreateTime(null);
        config.setUpdateTime(null);
        boolean toggled = this.updateById(config);
        if (toggled) {
            notifyChanged("启停指令 " + config.getPromptKey());
        }
        return toggled;
    }

    @Override
    @Transactional
    public boolean removeConfig(Long id) {
        PromptConfig exist = this.getById(id);
        if (exist == null) {
            throw new IllegalArgumentException("提示词配置不存在");
        }
        List<ChatModelBinding> refs = chatModelBindingMapper.selectByPromptId(id);
        if (!refs.isEmpty()) {
            throw new IllegalArgumentException("该指令已被「对话模型选择」的【" + sceneNames(refs)
                    + "】引用，请先解除绑定或改绑其它指令后再删除");
        }
        assertRuntimeKeywordNotDisabled(exist.getPromptKey(), "删除");
        boolean removed = this.removeById(id);
        if (removed) {
            notifyChanged("删除指令 " + exist.getPromptKey());
        }
        return removed;
    }

    /**
     * 标记每条指令是否为所属场景当前真正生效的那一条，规则与 {@link PromptSelector} 一致。
     * 判定必须基于"全部指令"而不是当前筛选结果，否则按场景/关键词筛选后会把偶然命中的一条误标为生效。
     */
    private void markEffective(List<PromptConfig> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<PromptConfig> all = this.lambdaQuery()
                .eq(PromptConfig::getStatus, 1)
                .orderByDesc(PromptConfig::getUpdateTime)
                .orderByAsc(PromptConfig::getId)
                .list();
        Set<Long> effectiveIds = PromptSelector.pickEffectiveByScene(all).values().stream()
                .map(PromptConfig::getId)
                .collect(Collectors.toSet());
        for (PromptConfig item : list) {
            item.setEffective(effectiveIds.contains(item.getId()) ? 1 : 0);
        }
    }

    /** 存在"启用中"的绑定引用该指令时，禁止停用，避免运行态出现"绑定启用但指令已停用"的矛盾配置 */
    private void assertNoActiveBinding(Long promptId, String action) {
        List<ChatModelBinding> active = chatModelBindingMapper.selectByPromptId(promptId).stream()
                .filter(b -> b.getEnabled() != null && b.getEnabled() == 1)
                .toList();
        if (!active.isEmpty()) {
            throw new IllegalArgumentException("该指令正在被「对话模型选择」的【" + sceneNames(active)
                    + "】使用，无法" + action + "；请先解除绑定或改绑其它指令");
        }
    }

    /**
     * 两张关键词表已被对话链路直接读取（{@link com.kzip.app.service.ContentGuard} 在调用大模型前做确定性匹配），
     * 不是"配了没用的普通配置"。若允许停用/删除，内容安全与转人工会在无任何提示的情况下失效，
     * 因此禁止这两项操作 —— 真要关闭拦截，应显式清空词表内容（行为可观察、可追溯）。
     */
    private void assertRuntimeKeywordNotDisabled(String promptKey, String action) {
        if (com.kzip.app.service.ContentGuard.KEY_UNSAFE.equals(promptKey)
                || com.kzip.app.service.ContentGuard.KEY_HANDOFF.equals(promptKey)) {
            throw new IllegalArgumentException("「" + promptKey
                    + "」已被对话链路使用（命中即在调用大模型前拦截），不能" + action
                    + "；如需关闭拦截，请清空其内容后保存");
        }
    }

    private static String sceneNames(List<ChatModelBinding> bindings) {
        return bindings.stream()
                .map(b -> StringUtils.hasText(b.getSceneName()) ? b.getSceneName() : b.getScene())
                .distinct()
                .collect(Collectors.joining("、"));
    }
}
