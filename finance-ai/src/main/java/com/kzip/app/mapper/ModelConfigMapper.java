package com.kzip.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kzip.app.entity.ModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {

    @Select("SELECT * FROM ai_model_config WHERE model_type = #{modelType} AND status = 1 AND is_deleted = 0")
    List<ModelConfig> selectActiveByType(@Param("modelType") String modelType);

    @Update("UPDATE ai_model_config SET is_default = 0 WHERE model_type = #{modelType} AND is_deleted = 0")
    int clearDefaultByType(@Param("modelType") String modelType);

    /**
     * 落库一次连通性测试结果。
     * 走自定义 SQL 而不复用 updateById：测试结果属于"系统写入"，
     * 用 @TableLogic + updateById 会把 is_deleted 一起带上、也容易与表单字段混在一起。
     */
    @Update("UPDATE ai_model_config SET test_status = #{testStatus}, test_time = NOW(), "
            + "test_message = #{testMessage} WHERE id = #{id} AND is_deleted = 0")
    int markTestResult(@Param("id") Long id,
                       @Param("testStatus") Integer testStatus,
                       @Param("testMessage") String testMessage);

    /**
     * 连接相关字段被改动后让既有测试结论失效（置为"未测试"）。
     * 否则"改了 API 地址/密钥/模型标识，却仍显示测试通过"，
     * 「只允许绑定测试通过的模型」这条约束就被绕过了。
     */
    @Update("UPDATE ai_model_config SET test_status = 0, test_time = NULL, "
            + "test_message = '配置已变更，请重新测试连通性' WHERE id = #{id} AND is_deleted = 0")
    int invalidateTestResult(@Param("id") Long id);
}