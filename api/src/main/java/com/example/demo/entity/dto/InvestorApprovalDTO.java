package com.example.demo.entity.dto;

import lombok.Data;

@Data
public class InvestorApprovalDTO {

    private Long investorId;

    private Integer status;

    private Boolean isIdCardOk;
}
