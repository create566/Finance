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

    boolean approveBorrowInfo(Long borrowInfoId, boolean approved, Long adminId);

    boolean deleteDraft(Long userId);

    /**
     * 查询最近一次借款申请状态：hasApply + 申请详情
     */
    Map<String, Object> getStatusByUser(Long userId);

    /**
     * 后台借款记录分页查询（按状态 + 借款人姓名/ID 筛选，按创建时间倒序）
     *
     * @param keyword 借款金额/用户ID 模糊搜索，可为空
     */
    Page<BorrowInfo> adminPageList(Integer pageNum, Integer pageSize, String keyword, Integer status);

    /**
     * 审核借款申请（通过/拒绝）
     * <p>会把审核管理员与审核时间写入 check_admin_id / check_time 留痕。
     *
     * @param id      借款申请ID
     * @param status  目标状态（2=通过, -1=拒绝）
     * @param adminId 操作管理员ID（后台登录态），仅用于留痕
     * @return 是否操作成功
     */
    boolean auditBorrowInfo(Long id, Integer status, Long adminId);

}