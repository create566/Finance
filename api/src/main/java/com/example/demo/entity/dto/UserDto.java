package com.example.demo.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {
    //User类对应表(后)  UserDTO对应前端接收(前)
    private Integer userType;
    private String mobile;

    private  String Password;
    private  String Passwordto;

}
