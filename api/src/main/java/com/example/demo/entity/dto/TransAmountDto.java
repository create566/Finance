package com.example.demo.entity.dto;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 充值/提现入参 DTO
 * 金额用 BigDecimal，与 UserAccountMapper 的参数类型保持一致，避免 Double→BigDecimal 转换
 */



@Data
@ApiModel(value = "交易金额DTO", description = "充值/提现时前端提交的金额参数")
public class TransAmountDto {

    @ApiModelProperty(value = "交易金额（元）", required = true, example = "100.00")
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

}
