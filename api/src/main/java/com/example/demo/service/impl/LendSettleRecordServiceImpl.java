package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.LendSettleRecord;
import com.example.demo.mapper.LendSettleRecordMapper;
import com.example.demo.service.LendSettleRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
public class LendSettleRecordServiceImpl extends ServiceImpl<LendSettleRecordMapper, LendSettleRecord>
        implements LendSettleRecordService {

    /**
     * 创建标的结清凭证
     * @param lendId 标的id
     * @param lendNo 标的业务编号
     * @param borrowerId 借款人id
     * @param totalPrincipal 总本金
     * @param totalInterest 总利息
     * @param totalPenalty 累计总罚息
     * @param settleTime 结清时间
     */
    @Override
    public void createSettleRecord(Long lendId, String lendNo, Long borrowerId,
                                   BigDecimal totalPrincipal, BigDecimal totalInterest,
                                   BigDecimal totalPenalty, LocalDateTime settleTime) {
        // 幂等：防止重复生成结清凭证，同一个标的只能存在一条结清记录
        long count = count(new LambdaQueryWrapper<LendSettleRecord>()
                .eq(LendSettleRecord::getLendId, lendId));
        if(count > 0){
            log.warn("标的{}已经生成结清凭证，跳过重复创建",lendId);
            return;
        }
        LendSettleRecord record = new LendSettleRecord();
        record.setLendId(lendId);
        record.setLendNo(lendNo);
        record.setBorrowerId(borrowerId);
        record.setTotalPrincipal(totalPrincipal);
        record.setTotalInterest(totalInterest);
        record.setTotalPenalty(totalPenalty);
        record.setSettleTime(settleTime);
        try {
            save(record);
            log.info("标的{}生成结清凭证成功", lendId);
        } catch (DuplicateKeyException e) {
            // 并发下两个回调可能同时通过上面的 count 检查，此时靠 uk_lend_id 唯一索引兜底：
            // 插入失败说明另一事务已写入，属幂等成功，不应把整个结清事务带崩。
            log.warn("标的{}结清凭证已被并发请求写入，跳过重复插入", lendId);
        }
    }

    /**
     * 根据标的ID查询结清凭证
     * @param lendId 标的id
     * @return 结清记录
     */
    @Override
    public LendSettleRecord getByLendId(Long lendId) {
        // 限定 1 条：历史脏数据（同一标的多条凭证）时 getOne 会直接抛异常，
        // 加唯一索引后理论上不会发生，但读取侧不应因脏数据而整体失败。
        return getOne(new LambdaQueryWrapper<LendSettleRecord>()
                .eq(LendSettleRecord::getLendId, lendId)
                .last("LIMIT 1"));
    }
}