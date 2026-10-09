package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_admin")
public class SysAdmin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String name;

    private String avatar;

    /**
     * 后台角色：{@code SUPER_ADMIN} / {@code ADMIN}，取值见
     * {@link com.wwfinance.common.auth.AdminRoles}。
     *
     * <p>2026-10-09（P2-11）新增。此前 sys_admin 无角色字段，token 里 token_role 恒为
     * "admin"，任何后台账号都能放款/下架/作废合同。现按最小权限建模：
     * 存量管理员在迁移脚本里置为 SUPER_ADMIN（避免现有环境功能失效），
     * <b>新建管理员默认 ADMIN</b>，高危动作需显式提权。</p>
     */
    private String role;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
