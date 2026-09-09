package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@TableName("integral_grade")
public class IntegralGrade {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer integralStart;

    private Integer integralEnd;

    private BigDecimal borrowAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;


}
