package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.Borrower;
import com.example.demo.entity.dto.BorrowerAuthDto;

public interface BorrowerService extends IService<Borrower> {

    /**
     * 提交实名认证：建借款人档案（认证中）+ 身份证附件 + 同步 user 表
     * @return borrowerId
     */
    Long submitAuth(Long userId, BorrowerAuthDto dto);

    /**
     * 审核实名认证：通过/拒绝，同步 user 表认证状态
     */
    void approveAuth(Long borrowerId, boolean approved);

    /**
     * 查当前用户的借款人档案（可能为 null）
     */
    Borrower getByUserId(Long userId);
}
