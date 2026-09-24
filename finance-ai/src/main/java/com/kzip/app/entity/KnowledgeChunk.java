package com.kzip.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long docId;

    private Long kbId;

    private Integer chunkIndex;

    private String summary;

    private Integer chunkSize;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}