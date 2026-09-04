package com.wwfinance.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BankBindDTO implements Serializable {

    private Integer agentId;
    private String agentUserId;
    private String personalName;
    private String mobile;
    private String idCard;
    private String returnUrl;
    private String notifyUrl;
    private Long timestamp;
    private String sign;
}