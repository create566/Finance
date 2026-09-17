
package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.LendSettleRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface LendSettleRecordService extends IService<LendSettleRecord> {
    void createSettleRecord(Long lendId, String lendNo, Long borrowerId,
                            BigDecimal totalPrincipal, BigDecimal totalInterest,
                            BigDecimal totalPenalty, LocalDateTime settleTime);

    LendSettleRecord getByLendId(Long lendId);
}