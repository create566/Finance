package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;


@Data
@TableName("knowledge_doc")
public class KnowledgeDoc {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private String title;

    private String source;

    private String content;

    private String fileName;

    private Integer fileSize;

    private String fileUrl;

    private Integer chunkCount;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}