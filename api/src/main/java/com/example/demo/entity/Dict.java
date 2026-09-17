package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典实体（树形两级：分类节点 + 选项节点）
 * 分类节点：dict_code 非空（如 returnMethod），value 为 null
 * 选项节点：parent_id 指向分类节点 id，value 为存储值
 */
@Data
@Accessors(chain = true)
@TableName("dict")
public class Dict implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("parent_id")
    private Long parentId;

    /** 字典分类编码（仅分类节点有值，如 returnMethod/education） */
    @TableField("dict_code")
    private String dictCode;

    /** 显示名称 */
    private String name;

    /** 存储值（选项节点用） */
    private Integer value;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}
