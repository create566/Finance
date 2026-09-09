package com.example.demo.service;

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

}