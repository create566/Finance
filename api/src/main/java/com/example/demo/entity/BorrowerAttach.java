package com.example.demo.entity;

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
 * 借款人上传资源表（身份证正反面、房产证、车等图片）
 * image_type: idCard1 身份证正面 / idCard2 身份证反面 / house 房产证 / car 车
 */
@Data
@Accessors(chain = true)
@TableName("borrower_attach")
public class BorrowerAttach implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("borrower_id")
    private Long borrowerId;

    @TableField("image_type")
    private String imageType;

    @TableField("image_url")
    private String imageUrl;

    @TableField("image_name")
    private String imageName;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("is_deleted")
    private Boolean deleted;
}
