package com.example.demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.Investor;
import com.example.demo.entity.dto.InvestorApprovalDTO;
import com.example.demo.entity.dto.InvestorAuthDto;

import java.util.Map;

/**
 * 投资人实名认证业务接口（对齐借款人 BorrowerService 模式）
 */
public interface InvestorService extends IService<Investor> {

    /**
     * 提交实名认证（未认证/认证失败可提交；已认证/认证中拒绝）
     *
     * @param userId 当前登录用户
     * @param dto    认证资料
     * @return 投资人档案ID
     */
    Long submitAuth(Long userId, InvestorAuthDto dto);

    /**
     * 后台审核（仅认证中状态可审核）
     */
    void approveAuth(InvestorApprovalDTO dto);

    /** 按用户ID查询投资人档案 */
    Investor getByUserId(Long userId);

    /** 查询当前用户认证状态 */
    Map<String, Object> getAuthStatus(Long userId);

    /** 后台分页列表 */
    Page<Investor> adminPageList(Integer pageNum, Integer pageSize, String keyword, Integer auditStatus);
}
