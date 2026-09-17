package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.ReconciliationRecord;

import java.time.LocalDateTime;

public interface ReconciliationService extends IService<ReconciliationRecord> {

    /**
     * 与银行对账：拉取银行流水，与本地 trans_flow 比对
     *
     * @param date 对账日期
     * @return 对账结果摘要
     */
    String reconcileWithBank(LocalDateTime date);
}
