package com.kzip.app.service;

import com.kzip.app.entity.PromptConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景生效指令选择规则（唯一真源）。
 *
 * <p>管理端「Prompt 设置」要显示"哪条在生效"，运行时 {@link PromptService} 要真正取用同一条，
 * 两处必须是同一套规则，否则会出现"页面显示的生效项 ≠ 实际跑的指令"。
 * 因此把规则抽到这里，双方共用。
 *
 * <p>规则（与 {@link PromptService#refreshPrompts()} 行为一致）：
 * <ol>
 *   <li>只考虑 status=1 且 is_deleted=0（listAll 查询已过滤）的条目；</li>
 *   <li>contentType=keywords 的关键词表不是系统指令，不参与选择；</li>
 *   <li>同一场景内，后出现的 is_default=1 会覆盖先前的；非默认条目只在场景首个候选时占位。</li>
 * </ol>
 * 传入的列表顺序即优先级顺序 —— 调用方需保证按 {@code update_time DESC} 排列，
 * 与 {@code PromptConfigServiceImpl.listAll} 的排序保持一致。
 */
public final class PromptSelector {

    private PromptSelector() {
    }

    /** 关键词表（违规词/转人工词）不是提示词，不参与场景指令选择 */
    public static boolean isKeywordConfig(PromptConfig item) {
        return item.getContentType() != null
                && "keywords".equalsIgnoreCase(item.getContentType().trim());
    }

    /**
     * 选出每个场景当前生效的那一条指令。
     * @param list 已按优先级排序（update_time DESC）的配置列表
     * @return scene -> 生效配置（LinkedHashMap，保持首次出现的场景顺序）
     */
    public static Map<String, PromptConfig> pickEffectiveByScene(List<PromptConfig> list) {
        Map<String, PromptConfig> effective = new LinkedHashMap<>();
        if (list == null) {
            return effective;
        }
        for (PromptConfig item : list) {
            if (item.getScene() == null || item.getScene().isBlank()) {
                continue;
            }
            if (item.getStatus() == null || item.getStatus() != 1) {
                continue;
            }
            if (isKeywordConfig(item)) {
                continue;
            }
            PromptConfig exist = effective.get(item.getScene());
            if (exist == null || (item.getIsDefault() != null && item.getIsDefault() == 1)) {
                effective.put(item.getScene(), item);
            }
        }
        return effective;
    }
}
