package com.example.demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.BorrowInfo;
import com.example.demo.entity.dto.BorrowInfoDto;

import java.util.List;
import java.util.Map;

public interface BorrowInfoService extends IService<BorrowInfo> {

    BorrowInfo submitBorrowInfo(Long userId, BorrowInfoDto dto);

    List<BorrowInfo> listByUser(Long userId);

    BorrowInfo getLatestByUser(Long userId);

    boolean approveBorrowInfo(Long borrowInfoId, boolean approved);

    boolean deleteDraft(Long userId);

    /**
     * 查询最近一次借款申请状态：hasApply + 申请详情
     */
    Map<String, Object> getStatusByUser(Long userId);

    /**
     * 后台借款记录分页查询（按状态筛选，按创建时间倒序）
     */
    Page<BorrowInfo> adminPageList(Integer pageNum, Integer pageSize, Integer status);

    /**
     * 审核借款申请（通过/拒绝）
     * @param id 借款申请ID
     * @param status 目标状态（2=通过, -1=拒绝）
     * @return 是否操作成功
     */
    boolean auditBorrowInfo(Long id, Integer status);

}