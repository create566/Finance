package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.BorrowInfo;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.entity.dto.BorrowInfoDto;
import com.example.demo.mapper.BorrowInfoMapper;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.example.demo.service.BorrowInfoService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BorrowInfoServiceImpl extends ServiceImpl<BorrowInfoMapper, BorrowInfo> implements BorrowInfoService {

    @Autowired
    private UserService userService;

    @Autowired
    private UserBindService userBindService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BorrowInfo submitBorrowInfo(Long userId, BorrowInfoDto dto) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(500, "用户不存在");
        }

        boolean isDraft = dto.getIsDraft() != null && dto.getIsDraft();

        if (!isDraft) {
            if (user.getUserType() == null || user.getUserType() != 2) {
                throw new ResourceNotFoundException(500, "只有借款人才能提交借款申请");
            }
            if (user.getBorrowAuthStatus() == null || user.getBorrowAuthStatus() != 2) {
                throw new ResourceNotFoundException(500, "请先完成借款人实名认证");
            }
            UserBind userBind = userBindService.getOne(new LambdaQueryWrapper<UserBind>()
                    .eq(UserBind::getUserId, userId)
                    .eq(UserBind::getStatus, 1));
            if (userBind == null) {
                throw new ResourceNotFoundException(500, "请先完成银行卡绑定");
            }
            BorrowInfo pending = this.getOne(new LambdaQueryWrapper<BorrowInfo>()
                    .eq(BorrowInfo::getUserId, userId)
                    .eq(BorrowInfo::getStatus, 1));
            if (pending != null) {
                throw new ResourceNotFoundException(500, "您有审核中的借款申请，请等待审核结果");
            }
        }

        BigDecimal rate = calculateRate(dto.getAmount(), dto.getPeriod());

        BorrowInfo draft = this.getOne(new LambdaQueryWrapper<BorrowInfo>()
                .eq(BorrowInfo::getUserId, userId)
                .eq(BorrowInfo::getStatus, 0));

        BorrowInfo borrowInfo;
        if (draft != null) {
            borrowInfo = draft;
        } else {
            borrowInfo = new BorrowInfo();
            borrowInfo.setUserId(userId);
            borrowInfo.setCreateTime(LocalDateTime.now());
        }

        borrowInfo.setAmount(dto.getAmount());
        borrowInfo.setPeriod(dto.getPeriod());
        borrowInfo.setReturnMethod(dto.getReturnMethod());
        borrowInfo.setMoneyUse(dto.getMoneyUse());
        borrowInfo.setBorrowYearRate(rate);
        borrowInfo.setStatus(isDraft ? 0 : 1);
        borrowInfo.setUpdateTime(LocalDateTime.now());

        if (draft != null) {
            this.updateById(borrowInfo);
        } else {
            this.save(borrowInfo);
        }

        return borrowInfo;
    }

    @Override
    public List<BorrowInfo> listByUser(Long userId) {
        return this.list(new LambdaQueryWrapper<BorrowInfo>()
                .eq(BorrowInfo::getUserId, userId)
                .orderByDesc(BorrowInfo::getCreateTime));
    }

    @Override
    public BorrowInfo getLatestByUser(Long userId) {
        return this.getOne(new LambdaQueryWrapper<BorrowInfo>()
                .eq(BorrowInfo::getUserId, userId)
                .orderByDesc(BorrowInfo::getCreateTime)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean approveBorrowInfo(Long borrowInfoId, boolean approved) {
        BorrowInfo borrowInfo = this.getById(borrowInfoId);
        if (borrowInfo == null) {
            throw new ResourceNotFoundException(500, "借款申请不存在");
        }
        if (borrowInfo.getStatus() == null || borrowInfo.getStatus() != 1) {
            throw new ResourceNotFoundException(500, "只有审核中的申请才能审批");
        }

        borrowInfo.setStatus(approved ? 2 : -1);
        borrowInfo.setUpdateTime(LocalDateTime.now());
        return this.updateById(borrowInfo);
    }

    @Override
    public boolean deleteDraft(Long userId) {
        return this.remove(new LambdaQueryWrapper<BorrowInfo>()
                .eq(BorrowInfo::getUserId, userId)
                .eq(BorrowInfo::getStatus, 0));
    }

    private BigDecimal calculateRate(BigDecimal amount, Integer period) {
        BigDecimal base = new BigDecimal("7.0");
        if (amount.compareTo(new BigDecimal("50000")) >= 0) {
            base = base.add(new BigDecimal("1.0"));
        }
        if (period >= 12) {
            base = base.add(new BigDecimal("1.0"));
        }
        return base.setScale(2, RoundingMode.HALF_UP);
    }

}