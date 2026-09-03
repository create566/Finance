package com.example.demo.entity.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
@ApiModel(value = "借款信息VO", description = "借款信息展示/传输对象")
public class BorrowInfoVO  implements Serializable {


    private static final long serialVersionUID = 1L;

    // ==================== 核心字段（与数据库映射） ====================

    @ApiModelProperty(value = "借款编号")
    private Long id;

    @ApiModelProperty(value = "借款人用户ID")
    private Long userId;

    @ApiModelProperty(value = "借款金额（元）")
    private BigDecimal amount;

    @ApiModelProperty(value = "借款期限（月）")
    private Integer period;

    @ApiModelProperty(value = "年化利率（%）")
    private BigDecimal borrowYearRate;

    @ApiModelProperty(value = "还款方式（1-等额本息 2-等额本金 3-每月还息一次还本 4-一次还本）")
    private Integer returnMethod;

    @ApiModelProperty(value = "还款方式描述（字典转换后的中文）")
    private String returnMethodDesc; // 扩展字段：方便前端直接展示中文

    @ApiModelProperty(value = "资金用途")
    private Integer moneyUse;

    @ApiModelProperty(value = "资金用途描述")
    private String moneyUseDesc;

    @ApiModelProperty(value = "状态（0：未提交，1：审核中，2：审核通过，-1：审核不通过）")
    private Integer status;

    @ApiModelProperty(value = "状态描述（待审核/审核通过/已拒贷等）")
    private String statusDesc;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;

    // ==================== 扩展字段（关联查询补全，数据库无对应列） ====================

    @ApiModelProperty(value = "借款人姓名（关联user表）")
    private String borrowerName;

    @ApiModelProperty(value = "借款人手机号（关联user表）")
    private String borrowerMobile;

    @ApiModelProperty(value = "借款人身份证号（关联user表，脱敏后展示）")
    private String borrowerIdCard;

    // ==================== 前端辅助字段 ====================

    @ApiModelProperty(value = "剩余还款期数（计算得出）")
    private Integer remainingPeriod;

    @ApiModelProperty(value = "是否逾期（业务计算）")
    private Boolean overdue;

    @ApiModelProperty(value = "逾期天数")
    private Integer overdueDays;

    @ApiModelProperty(value = "扩展参数（用于临时传递额外数据，如分页排序参数）")
    private Map<String, Object> extraParams = new HashMap<>();

}
