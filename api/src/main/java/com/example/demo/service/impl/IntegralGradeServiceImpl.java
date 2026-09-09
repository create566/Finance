package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.IntegralGrade;
import com.example.demo.mapper.IntegralGradeMapper;
import com.example.demo.service.IntegralGradeService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class IntegralGradeServiceImpl extends ServiceImpl<IntegralGradeMapper, IntegralGrade> implements IntegralGradeService {

    @Override
    public boolean saveIntegralGrade(IntegralGrade integralGrade) {
        checkRange(integralGrade);
        long count = lambdaQuery()
                .and(w -> w.le(IntegralGrade::getIntegralStart, integralGrade.getIntegralEnd())
                        .ge(IntegralGrade::getIntegralEnd, integralGrade.getIntegralStart()))
                .count();
        if (count > 0) {
            throw new RuntimeException("积分区间与已有等级重叠");
        }
        return save(integralGrade);
    }

    @Override
    public boolean updateIntegralGrade(IntegralGrade integralGrade) {
        checkRange(integralGrade);
        long count = lambdaQuery()
                .ne(IntegralGrade::getId, integralGrade.getId())
                .and(w -> w.le(IntegralGrade::getIntegralStart, integralGrade.getIntegralEnd())
                        .ge(IntegralGrade::getIntegralEnd, integralGrade.getIntegralStart()))
                .count();
        if (count > 0) {
            throw new RuntimeException("积分区间与已有等级重叠");
        }
        return updateById(integralGrade);
    }

    @Override
    public BigDecimal getBorrowAmountByIntegral(Integer integral) {
        if (integral == null || integral < 0) {
            return BigDecimal.ZERO;
        }
        IntegralGrade grade = getOne(new LambdaQueryWrapper<IntegralGrade>()
                .le(IntegralGrade::getIntegralStart, integral)
                .ge(IntegralGrade::getIntegralEnd, integral)
                .last("LIMIT 1"));
        return grade != null ? grade.getBorrowAmount() : BigDecimal.ZERO;
    }

    @Override
    public IPage<IntegralGrade> pageList(Integer pageNum, Integer pageSize) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<IntegralGrade>()
                        .orderByAsc(IntegralGrade::getIntegralStart));
    }

    private void checkRange(IntegralGrade integralGrade) {
        if (integralGrade.getIntegralStart() == null || integralGrade.getIntegralEnd() == null) {
            throw new RuntimeException("积分起止不能为空");
        }
        if (integralGrade.getIntegralStart() > integralGrade.getIntegralEnd()) {
            throw new RuntimeException("起始积分不能大于结束积分");
        }
        if (integralGrade.getIntegralStart() < 0) {
            throw new RuntimeException("积分不能为负数");
        }
        if (integralGrade.getBorrowAmount() == null
                || integralGrade.getBorrowAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("可借额度必须大于0");
        }
    }
}