package com.kzip.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kzip.app.entity.ChatModelBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChatModelBindingMapper extends BaseMapper<ChatModelBinding> {

    /** 运行时按场景取生效绑定（enabled=1），scene 有唯一约束，最多一行 */
    @Select("SELECT * FROM ai_chat_model_binding WHERE scene = #{scene} AND enabled = 1 AND is_deleted = 0 LIMIT 1")
    ChatModelBinding selectActiveByScene(@Param("scene") String scene);

    @Select("SELECT * FROM ai_chat_model_binding WHERE scene = #{scene} AND enabled = 1 AND is_deleted = 0")
    ChatModelBinding selectByScene(@Param("scene") String scene);

    @Select("SELECT * FROM ai_chat_model_binding WHERE enabled = 1 AND is_deleted = 0")
    List<ChatModelBinding> selectAllEnabled();

    /**
     * 管理端列表：返回全部未删除的绑定（含禁用行）。
     * 之前的实现只查 enabled=1，导致全部种子数据（enabled=0）时页面显示"暂无数据"，
     * 管理员连启用入口都看不到。
     */
    @Select("SELECT * FROM ai_chat_model_binding WHERE is_deleted = 0 ORDER BY id ASC")
    List<ChatModelBinding> selectAllForAdmin();

    /**
     * 忽略逻辑删除标记查一行，用于新增时"复活"同场景的历史软删除记录。
     * 表上有 UNIQUE KEY uk_scene(scene)，若直接 insert 会撞唯一键。
     */
    @Select("SELECT * FROM ai_chat_model_binding WHERE scene = #{scene} LIMIT 1")
    ChatModelBinding selectAnyByScene(@Param("scene") String scene);

    /** 引用检查：哪些生效中的绑定引用了该模型（用于删除/禁用模型前拦截） */
    @Select("SELECT * FROM ai_chat_model_binding WHERE model_id = #{modelId} AND is_deleted = 0")
    List<ChatModelBinding> selectByModelId(@Param("modelId") Long modelId);

    /** 引用检查：哪些生效中的绑定引用了该 Prompt 指令 */
    @Select("SELECT * FROM ai_chat_model_binding WHERE prompt_id = #{promptId} AND is_deleted = 0")
    List<ChatModelBinding> selectByPromptId(@Param("promptId") Long promptId);

    /** 引用检查：哪些生效中的绑定引用了该知识库 */
    @Select("SELECT * FROM ai_chat_model_binding WHERE kb_id = #{kbId} AND is_deleted = 0")
    List<ChatModelBinding> selectByKbId(@Param("kbId") Long kbId);

    /**
     * 知识库被删除时解除绑定引用，避免留下指向已删知识库的悬空 kb_id
     * （悬空后再次保存该绑定会因"知识库不存在"直接失败）。
     */
    @Update("UPDATE ai_chat_model_binding SET kb_id = NULL, kb_name = '—', update_time = NOW() "
            + "WHERE kb_id = #{kbId} AND is_deleted = 0")
    int clearKbByKbId(@Param("kbId") Long kbId);

    /**
     * 模型配置里的模型标识被改名后，同步刷新绑定的冗余列。
     * binding.model_name 会被 ReActService 当作真实模型标识传给大模型接口，
     * 不同步就会出现"模型配置里改成了 qwen3.8-max，运行时还在请求旧名字"的静默失效。
     */
    @Update("UPDATE ai_chat_model_binding SET model_name = #{modelName}, update_time = NOW() "
            + "WHERE model_id = #{modelId} AND is_deleted = 0")
    int syncModelName(@Param("modelId") Long modelId, @Param("modelName") String modelName);

    /**
     * 一次性写入全部可变字段并清除逻辑删除标记。
     * 不走 MyBatis-Plus updateById：@TableLogic 会自动给 WHERE 追加 is_deleted = 0，
     * 软删除的历史行无法被"复活"；同时该语句不触碰 create_time，避免前端回传的时间覆盖创建时间。
     */
    @Update("UPDATE ai_chat_model_binding SET is_deleted = 0, scene_name = #{b.sceneName}, "
            + "model_id = #{b.modelId}, model_name = #{b.modelName}, "
            + "prompt_id = #{b.promptId}, prompt_key = #{b.promptKey}, "
            + "kb_id = #{b.kbId,jdbcType=BIGINT}, kb_name = #{b.kbName}, "
            + "enabled = #{b.enabled}, remark = #{b.remark}, update_time = NOW() "
            + "WHERE id = #{b.id}")
    int applyBinding(@Param("b") ChatModelBinding b);
}
