package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.BorrowInfo;
import com.example.demo.entity.dto.BorrowInfoDto;

import java.util.List;

public interface BorrowInfoService extends IService<BorrowInfo> {

    BorrowInfo submitBorrowInfo(Long userId, BorrowInfoDto dto);

    List<BorrowInfo> listByUser(Long userId);

    BorrowInfo getLatestByUser(Long userId);

    boolean approveBorrowInfo(Long borrowInfoId, boolean approved);

    boolean deleteDraft(Long userId);

}