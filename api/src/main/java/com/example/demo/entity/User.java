package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import com.baomidou.mybatisplus.annotation.TableLogic;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@Accessors(chain = true)
@TableName("user")
public class User  implements Serializable {

    private static final long serialVersionUID = 1L;


  @TableId(value = "id",type = IdType.AUTO)
    private Long id;
    private Integer userType;
    private String mobile;

    @JsonIgnore
    private String password;

    private String name;


    /*
    *状态
     */
    private Integer status;


    /**
     * 用户昵称
     */
    @TableField("nick_name")
    private String nickName;



    /**
     * 身份证号
     */
    @TableField("id_card")
    private String idCard;

    /**
     * 邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 微信用户标识openid
     */
    @TableField("openid")
    private String openid;

    /**
     * 头像
     */
    @TableField("head_img")
    private String headImg;

    /**
     * 绑定状态（0：未绑定，1：绑定成功 -1：绑定失败）
     */
    @TableField("bind_status")
    private Integer bindStatus;

    /**
     * 借款人认证状态（0：未认证 1：认证中 2：认证通过 -1：认证失败）
     */
    @TableField("borrow_auth_status")
    private Integer borrowAuthStatus;

    /**
     * 投资人认证状态（0：未认证 1：认证中 2：认证通过 -1：认证失败）
     */
    @TableField("invest_auth_status")
    private Integer investAuthStatus;

    /**
     * 绑定账户协议号
     */
    @TableField("bind_code")
    private String bindCode;

    /**
     * 用户积分
     */
    @TableField("integral")
    private Integer integral;


    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除(1:已删除，0:未删除)
     */
    @TableLogic
    @TableField("is_deleted")
    private Boolean deleted;

}
