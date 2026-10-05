package com.kzip.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kzip.app.entity.ChatModelBinding;

import java.util.List;
import java.util.Map;

public interface ChatModelBindingService extends IService<ChatModelBinding> {

    /** 管理端列表：全部未删除（含禁用） */
    List<ChatModelBinding> listAll();

    /** 运行时可配置的处理环节（scene -> 中文名），与 ReActService 实际取用的 scene 保持一致 */
    Map<String, String> supportedScenes();

    /**
     * 每个处理环节当前"实际会调用哪个模型"，含来源说明。
     * 复刻 {@code ReActService.resolveSceneModel} 的三级优先级：启用中的绑定 → 场景 Prompt 配置 → 代码内置默认值。
     * 管理端此前只配得进去、看不到生效结果，容易出现"改了没反应"的困惑。
     */
    List<Map<String, Object>> effectiveConfigs();

    /** 新增绑定：scene 已存在（含历史软删除）时复活并覆盖，否则插入 */
    boolean saveBinding(ChatModelBinding binding);

    /** 更新绑定：只允许改模型/指令/知识库/启用状态/备注，名称类字段由服务端解析 */
    boolean updateBinding(ChatModelBinding binding);

    boolean removeBinding(Long id);

    boolean toggleEnabled(Long id);

    ChatModelBinding getByScene(String scene);
}
