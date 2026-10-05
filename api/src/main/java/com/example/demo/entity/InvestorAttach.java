package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投资人上传资源表（身份证正反面）
 * image_type: idCard1 身份证正面 / idCard2 身份证反面
 */
@Data
@Accessors(chain = true)
@TableName("investor_attach")
public class InvestorAttach implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("investor_id")
    private Long investorId;

    @TableField("image_type")
    private String imageType;

    @TableField("image_url")
    private String imageUrl;

    @TableField("image_name")
    private String imageName;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("is_deleted")
    private Boolean deleted;
}
