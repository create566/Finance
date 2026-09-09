package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.Borrower;
import com.example.demo.entity.dto.BorrowerAuthDto;
import com.example.demo.entity.dto.BorrowerApprovalDTO;

import java.util.Map;

public interface BorrowerService extends IService<Borrower> {

    /**
     * 提交实名认证：校验用户/重复提交 → 建借款人档案（认证中）+ 身份证附件 + 同步 user 表
     * 业务规则不满足时抛 RuntimeException，由 GlobalExceptionHandler 统一封装
     * @return borrowerId
     */
    Long submitAuth(Long userId, BorrowerAuthDto dto);

    /**
     * 审核实名认证：校验记录存在/状态为认证中 → 通过/拒绝，同步 user 表认证状态
     * 审批通过时按 BorrowerApprovalDTO 打分累加到 user.integral
     */
//    void approveAuth(Long borrowerId, boolean approved);
     void approveAuth(BorrowerApprovalDTO dto);
    /**
     * 查当前用户的借款人档案（可能为 null）
     */
    Borrower getByUserId(Long userId);

    /**
     * 查询认证状态：组装用户认证状态 + 借款人档案
     */
    Map<String, Object> getAuthStatus(Long userId);
}
