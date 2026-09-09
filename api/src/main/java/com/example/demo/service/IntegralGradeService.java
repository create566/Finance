package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.IntegralGrade;

public interface IntegralGradeService extends IService<IntegralGrade> {

    boolean saveIntegralGrade(IntegralGrade integralGrade);

    boolean updateIntegralGrade(IntegralGrade integralGrade);

    IPage<IntegralGrade> pageList(Integer pageNum, Integer pageSize);

    java.math.BigDecimal getBorrowAmountByIntegral(Integer integral);

}