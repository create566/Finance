

package com.example.demo.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDto implements Serializable {
    private Long id;
    private Integer userType;
    private String mobile;
    private String code;
    private String password;
    private String passwordto;
    private String name;
    private String nickName;
    private String idCard;
    private String email;
    private String headImg;
    private Integer bindStatus;
    private String bindCode;
    private Integer borrowAuthStatus;
    private Integer integral;
}