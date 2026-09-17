package com.example.demo.entity.dto;

import lombok.Data;

@Data
public class BorrowerApprovalDTO {

    private Long borrowerId;

    private Integer status;

    private Boolean isIdCardOk;

    private Boolean isHouseOk;

    private Boolean isCarOk;

    private Integer infoIntegral;
}
