package com.kzip.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kzip.app.entity.PromptConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PromptConfigMapper extends BaseMapper<PromptConfig> {

    /**
     * 取某场景下优先级最高的「系统指令」。
     * 排除 content_type='keywords'：关键词表也挂了 scene，若被当成场景指令返回，
     * 会让 {@code ChatModelBindingServiceImpl} 用关键词表去兜底解析场景模型。
     */
    @Select("SELECT * FROM prompt_config WHERE scene = #{scene} AND status = 1 AND is_deleted = 0 "
            + "AND (content_type IS NULL OR content_type <> 'keywords') "
            + "ORDER BY is_default DESC, id ASC")
    List<PromptConfig> selectActiveByScene(@Param("scene") String scene);

    /**
     * 清掉某场景下所有指令的「默认」标记（同一场景只允许一条默认指令）。
     *
     * <p>必须排除 content_type='keywords' 的关键词表：关键词表也填了 scene（历史上 UNSAFE_KEYWORDS 挂在 risk、
     * HANDOFF_KEYWORDS 挂在 chat），且种子里 is_default=1。若不排除，编辑一次「转人工关键词」就会把
     * scene=chat 下真正的默认指令 CS_SYSTEM_PROMPT 的默认标记一并清掉，导致客服对话场景意外换用别的指令。
     * 关键词表本身不参与场景指令选择（见 {@code PromptSelector#isKeywordConfig}）。
     */
    @Update("UPDATE prompt_config SET is_default = 0 "
            + "WHERE scene = #{scene} AND is_deleted = 0 "
            + "AND (content_type IS NULL OR content_type <> 'keywords')")
    int clearDefaultByScene(@Param("scene") String scene);
}