package com.example.demo.entity.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;

@Data
@ApiModel(value = "借款申请DTO", description = "借款人提交借款申请时的入参")
public class BorrowInfoDto {

    @ApiModelProperty(value = "借款金额（元）", required = true, example = "10000")
    @NotNull(message = "借款金额不能为空")
    @DecimalMin(value = "1000", message = "借款金额不能低于1000元")
    private BigDecimal amount;

    @ApiModelProperty(value = "借款期限（月）", required = true, example = "12")
    @NotNull(message = "借款期限不能为空")
    @Min(value = 1, message = "借款期限最少1个月")
    @Max(value = 36, message = "借款期限最多36个月")
    private Integer period;

    @ApiModelProperty(value = "还款方式：1-等额本息 2-等额本金 3-每月还息一次还本 4-一次还本", required = true, example = "1")
    @NotNull(message = "还款方式不能为空")
    @Min(value = 1, message = "还款方式无效")
    @Max(value = 4, message = "还款方式无效")
    private Integer returnMethod;

    @ApiModelProperty(value = "资金用途：1-消费 2-经营 3-教育 4-医疗 5-其他", required = true, example = "1")
    @NotNull(message = "资金用途不能为空")
    private Integer moneyUse;

    @ApiModelProperty(value = "是否保存为草稿（true=草稿，false=正式提交，默认false）", example = "false")
    private Boolean isDraft = false;

}