package com.example.demo.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 实名认证提交入参（前端接收）
 * 参数格式校验用注解声明，Controller 用 @Valid 触发，
 * 校验失败由 GlobalExceptionHandler 统一返回 PccAjaxResult(400, msg)
 */
@Data
public class BorrowerAuthDto {
    /** 真实姓名 */
    @NotBlank(message = "姓名不能为空")
    private String name;

    /** 身份证号（18位，末位可为X） */
    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "^\\d{17}[\\dXx]$", message = "身份证号格式错误")
    private String idCard;

    /** 身份证正面图片URL（先上传 finance-oss 拿到的地址） */
    @NotBlank(message = "请先上传身份证正面照片")
    private String idCardFrontUrl;

    /** 身份证反面图片URL */
    @NotBlank(message = "请先上传身份证反面照片")
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
