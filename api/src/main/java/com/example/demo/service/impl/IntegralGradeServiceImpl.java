package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.IntegralGrade;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.IntegralGradeMapper;
import com.example.demo.service.IntegralGradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import com.example.demo.utils.PageUtils;

@Slf4j
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
            throw new BusinessException(400, "积分区间与已有等级重叠");
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
            throw new BusinessException(400, "积分区间与已有等级重叠");
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
        if (grade == null) {
            // 常见于积分档位表存在"区间空洞"（旧档被逻辑删除、新档未补），
            // 此时用户可借额度会被静默判为 0，借款申请报"超过可借额度"，排查困难
            log.warn("积分未匹配到可借额度档位，integral={}，按 0 额度处理，请检查 integral_grade 区间是否连续", integral);
            return BigDecimal.ZERO;
        }
        return grade.getBorrowAmount();
    }

    @Override
    public IPage<IntegralGrade> pageList(Integer pageNum, Integer pageSize) {
        return page(PageUtils.of(pageNum, pageSize),
                new LambdaQueryWrapper<IntegralGrade>()
                        .orderByAsc(IntegralGrade::getIntegralStart));
    }

    private void checkRange(IntegralGrade integralGrade) {
        if (integralGrade.getIntegralStart() == null || integralGrade.getIntegralEnd() == null) {
            throw new BusinessException(400, "积分起止不能为空");
        }
        if (integralGrade.getIntegralStart() > integralGrade.getIntegralEnd()) {
            throw new BusinessException(400, "起始积分不能大于结束积分");
        }
        if (integralGrade.getIntegralStart() < 0) {
            throw new BusinessException(400, "积分不能为负数");
        }
        if (integralGrade.getBorrowAmount() == null
                || integralGrade.getBorrowAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "可借额度必须大于0");
        }
    }
}