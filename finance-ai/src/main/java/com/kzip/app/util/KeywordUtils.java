package com.kzip.app.util;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 关键词表解析与文本归一化。
 *
 * <p>存在的两个坑：
 * <ol>
 *   <li><b>分隔符不统一</b>：种子里 {@code UNSAFE_KEYWORDS} / {@code HANDOFF_KEYWORDS} 的内容是
 *       <em>逗号分隔的单行</em>（"暴力,色情,赌博,…"），而管理端页面写的是"每行一个关键词"并按行计数，
 *       结果整行被当成一个超长关键词，永远不可能命中。这里统一按 逗号/中文逗号/顿号/换行 切分。</li>
 *   <li><b>标点可绕过</b>：用户输入"转 人 工！"或"转人工？"时，直接 contains 匹配不到"转人工"。
 *       归一化时去掉所有空白与标点，只保留汉字和英数字，避免加个标点就绕过拦截。</li>
 * </ol>
 */
public final class KeywordUtils {

    private KeywordUtils() {
    }

    /** 只保留汉字与英数字，其余（空白、标点、emoji 等）全部剔除 */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase().replaceAll("[^\\p{IsHan}a-z0-9]", "");
    }

    /**
     * 解析关键词表内容。
     * @param raw 数据库中的原始文本（每行一个、逗号分隔、顿号分隔都支持）
     * @return 归一化后的关键词集合，去重且保持出现顺序（LinkedHashSet 便于日志排查）
     */
    public static Set<String> split(String raw) {
        Set<String> tokens = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return tokens;
        }
        for (String part : raw.split("[,，、;；\\n\\r]+")) {
            String token = normalize(part);
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** 关键词个数（管理端列表展示用，必须与 {@link #split} 的切分规则一致） */
    public static int count(String raw) {
        return split(raw).size();
    }
}
