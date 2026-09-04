package com.example.demo.entity.dto;

import lombok.Data;

/**
 * 实名认证提交入参（前端接收）
 */
@Data
public class BorrowerAuthDto {
    /** 真实姓名 */
    private String name;
    /** 身份证号（18位） */
    private String idCard;
    /** 身份证正面图片URL（先上传 finance-oss 拿到的地址） */
    private String idCardFrontUrl;
    /** 身份证反面图片URL */
    private String idCardBackUrl;
    /** 性别（1男 0女） */
    private Integer sex;
    /** 年龄 */
    private Integer age;
    /** 学历 */
    private Integer education;
    /** 是否结婚（1是 0否） */
    private Integer isMarry;
    /** 行业 */
    private Integer industry;
    /** 月收入 */
    private Integer income;
    /** 还款来源 */
    private Integer returnSource;
    /** 联系人名称 */
    private String contactsName;
    /** 联系人手机 */
    private String contactsMobile;
    /** 联系人关系 */
    private Integer contactsRelation;
}
