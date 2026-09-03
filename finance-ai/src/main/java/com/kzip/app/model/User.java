package com.kzip.app.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;


@Data
public class User implements Serializable {

    private Long id;           // 用户ID
    private String username;   // 用户名
    private String password;   // 密码（实际项目会加密存储）
    private String email;      // 邮箱
    private Integer age;       // 年龄
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
}