package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合同签署存证实体（映射 contract_sign_record 表）
 * <p>
 * 防抵赖四要素：签名图 + 签署 IP + User-Agent + 签署时间，连同签署那一刻的正文哈希一起存证。
 * uk_contract_signer(contract_id, signer_id) 保证同一人同一合同只签一次（幂等）。
 */
@Data
@TableName("contract_sign_record")
public class ContractSignRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("contract_id")
    private Long contractId;

    /** 签署人 userId */
    @TableField("signer_id")
    private Long signerId;

    /** PARTY_A / PARTY_B */
    @TableField("signer_role")
    private String signerRole;

    @TableField("signer_name")
    private String signerName;

    @TableField("signer_id_card")
    private String signerIdCard;

    /** 手写签名图 URL */
    @TableField("sign_img_url")
    private String signImgUrl;

    /** 签署时校验的正文哈希 */
    @TableField("content_hash")
    private String contentHash;

    @TableField("sign_ip")
    private String signIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("sign_time")
    private LocalDateTime signTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
