package com.kzip.app.service;

import com.kzip.app.util.KeywordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 对话内容前置拦截（确定性规则，不经过大模型）。
 *
 * <p>背景：数据库里的两张关键词表 {@code UNSAFE_KEYWORDS}（风控违规词）与
 * {@code HANDOFF_KEYWORDS}（转人工词）此前<em>从未被任何代码读取</em>，管理端却可以编辑它们，
 * 属于"配了等于没配"。本类把它们接到对话入口：
 *
 * <ul>
 *   <li>命中违规词 → 直接返回合规话术，不调用大模型（省 token，也避免被诱导绕过）；</li>
 *   <li>命中转人工词 → 直接返回转人工提示，不调用大模型（更快，且不会被模型改写成别的意思）。</li>
 * </ul>
 *
 * <p>词表内容取自 {@link PromptService} 的缓存，管理端保存后经
 * {@link PromptService.ChangedEvent} 立即生效，最多还有 5 分钟的定时刷新兜底。
 *
 * <p>判定顺序：违规优先于转人工（先拦后转）。
 */
@Service
public class ContentGuard {

    private static final Logger log = LoggerFactory.getLogger(ContentGuard.class);

    /** 违规词表在 prompt_config 中的指令标识 */
    public static final String KEY_UNSAFE = "UNSAFE_KEYWORDS";
    /** 转人工词表在 prompt_config 中的指令标识 */
    public static final String KEY_HANDOFF = "HANDOFF_KEYWORDS";

    /** 命中违规词的固定回复（确定性规则，不走大模型，故为常量） */
    public static final String UNSAFE_REPLY =
            "抱歉，您的问题涉及违规内容，我无法回答。如需帮助请联系人工客服。";
    /** 命中转人工词的固定回复 */
    public static final String HANDOFF_REPLY =
            "好的，正在为您转接人工客服，请稍候。";

    public enum Verdict {
        /** 正常放行，继续走大模型 */
        PASS,
        /** 命中违规词，拦截至此 */
        UNSAFE,
        /** 命中转人工词，转人工处理 */
        HANDOFF
    }

    private final PromptService promptService;

    public ContentGuard(PromptService promptService) {
        this.promptService = promptService;
    }

    public Verdict check(String question) {
        String normalized = KeywordUtils.normalize(question);
        if (normalized.isEmpty()) {
            return Verdict.PASS;
        }
        if (hits(KEY_UNSAFE, normalized)) {
            log.warn("内容安全拦截：命中违规关键词，问题长度={}", question.length());
            return Verdict.UNSAFE;
        }
        if (hits(KEY_HANDOFF, normalized)) {
            log.info("命中转人工关键词，直接转人工，问题长度={}", question.length());
            return Verdict.HANDOFF;
        }
        return Verdict.PASS;
    }

    public boolean isBlocked(Verdict verdict) {
        return verdict != null && verdict != Verdict.PASS;
    }

    /** 拦截时对用户说的话 */
    public String replyFor(Verdict verdict) {
        return verdict == Verdict.UNSAFE ? UNSAFE_REPLY : HANDOFF_REPLY;
    }

    /** 透传给前端的标记值，便于前端区分气泡样式（unsafe 显示为警示，handoff 显示为转人工提示） */
    public String tagOf(Verdict verdict) {
        return verdict == Verdict.UNSAFE ? "unsafe" : "handoff";
    }

    /** 词表是否为空（管理端提示用：词表为空时拦截不会生效） */
    public boolean keywordTableEmpty(String promptKey) {
        return promptService.getKeywordTokens(promptKey).isEmpty();
    }

    private boolean hits(String promptKey, String normalizedQuestion) {
        for (String token : promptService.getKeywordTokens(promptKey)) {
            if (normalizedQuestion.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
