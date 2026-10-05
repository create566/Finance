package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息实体（映射 message 表）
 */
@Data
@TableName("message")
public class Message {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 接收用户ID */
    @TableField("user_id")
    private Long userId;

    /** 消息标题 */
    @TableField("title")
    private String title;

    /** 消息内容 */
    @TableField("content")
    private String content;

    /** 类型，取值见 {@link com.example.demo.enums.MessageTypeEnum} */
    @TableField("message_type")
    private Integer messageType;

    /**
     * 是否已读：0未读 1已读
     * 属性名避开 MySQL 保留字 read，但 JSON 字段名就是属性名 readFlag ——
     * 前端必须按 readFlag 读，写成 isRead 会永远取不到值（消息列表全是"已读"、且点不掉未读）。
     */
    @TableField("`is_read`")
    private Integer readFlag;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}
