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
 * 借款人档案表（实名认证）
 * 状态：0 未认证 / 1 认证中 / 2 认证通过 / -1 认证失败
 */
@Data
@Accessors(chain = true)
@TableName("borrower")
public class Borrower implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("name")
    private String name;

    @TableField("id_card")
    private String idCard;

    @TableField("mobile")
    private String mobile;

    @TableField("sex")
    private Integer sex;

    @TableField("age")
    private Integer age;

    @TableField("education")
    private Integer education;

    @TableField("is_marry")
    private Integer isMarry;

    @TableField("industry")
    private Integer industry;

    @TableField("income")
    private Integer income;

    @TableField("return_source")
    private Integer returnSource;

    @TableField("contacts_name")
    private String contactsName;

    @TableField("contacts_mobile")
    private String contactsMobile;

    @TableField("contacts_relation")
    private Integer contactsRelation;

    @TableField("status")
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("is_deleted")
    private Boolean deleted;
}
