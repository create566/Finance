package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.Borrower;
import com.example.demo.entity.BorrowerAttach;
import com.example.demo.entity.User;
import com.example.demo.entity.dto.BorrowerAuthDto;
import com.example.demo.enums.BorrowerAuthStatusEnum;
import com.example.demo.enums.UserTypeEnum;
import com.example.demo.exception.BusinessException;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.example.demo.mapper.BorrowerAttachMapper;
import com.example.demo.mapper.BorrowerMapper;
import com.example.demo.service.BorrowerService;
import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.entity.dto.BorrowerApprovalDTO;
import com.example.demo.service.UserIntegralService;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class BorrowerServiceImpl extends ServiceImpl<BorrowerMapper, Borrower> implements BorrowerService {

    @Resource
    private BorrowerAttachMapper borrowerAttachMapper;

    @Resource
    private UserService userService;
    @Resource
    private UserIntegralService userIntegralService;

    @Autowired
    private com.example.demo.service.MessageService messageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitAuth(Long userId, BorrowerAuthDto dto) {
        User user = userService.getById(userId);

        LambdaQueryWrapper<Borrower> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Borrower::getUserId, userId);
        Borrower existingBorrower = getOne(wrapper);

        if (existingBorrower != null) {
            if (BorrowerAuthStatusEnum.AUTHED.getCode().equals(existingBorrower.getStatus())) {
                throw new BusinessException(400, "已完成认证，无需重复提交");
            }
            if (BorrowerAuthStatusEnum.AUTHING.getCode().equals(existingBorrower.getStatus())) {
                throw new BusinessException(400, "已提交认证，请等待审核");
            }
            existingBorrower.setName(dto.getName())
                    .setIdCard(dto.getIdCard())
                    .setMobile(user.getMobile())
                    .setSex(dto.getSex())
                    .setAge(dto.getAge())
                    .setEducation(dto.getEducation())
                    .setIsMarry(dto.getIsMarry())
                    .setIndustry(dto.getIndustry())
                    .setIncome(dto.getIncome())
                    .setReturnSource(dto.getReturnSource())
                    .setContactsName(dto.getContactsName())
                    .setContactsMobile(dto.getContactsMobile())
                    .setContactsRelation(dto.getContactsRelation())
                    .setStatus(BorrowerAuthStatusEnum.AUTHING.getCode());
            updateById(existingBorrower);

            LambdaQueryWrapper<BorrowerAttach> attachWrapper = new LambdaQueryWrapper<>();
            attachWrapper.eq(BorrowerAttach::getBorrowerId, existingBorrower.getId());
            borrowerAttachMapper.delete(attachWrapper);

            borrowerAttachMapper.insert(new BorrowerAttach()
                    .setBorrowerId(existingBorrower.getId())
                    .setImageType("idCard1")
                    .setImageUrl(dto.getIdCardFrontUrl()));
            borrowerAttachMapper.insert(new BorrowerAttach()
                    .setBorrowerId(existingBorrower.getId())
                    .setImageType("idCard2")
                    .setImageUrl(dto.getIdCardBackUrl()));

            user.setName(dto.getName());
            user.setIdCard(dto.getIdCard());
            user.setBorrowAuthStatus(BorrowerAuthStatusEnum.AUTHING.getCode());
            userService.updateById(user);

            return existingBorrower.getId();
        }

        Borrower borrower = new Borrower()
                .setUserId(userId)
                .setName(dto.getName())
                .setIdCard(dto.getIdCard())
                .setMobile(user.getMobile())
                .setSex(dto.getSex())
                .setAge(dto.getAge())
                .setEducation(dto.getEducation())
                .setIsMarry(dto.getIsMarry())
                .setIndustry(dto.getIndustry())
                .setIncome(dto.getIncome())
                .setReturnSource(dto.getReturnSource())
                .setContactsName(dto.getContactsName())
                .setContactsMobile(dto.getContactsMobile())
                .setContactsRelation(dto.getContactsRelation())
                .setStatus(BorrowerAuthStatusEnum.AUTHING.getCode());
        save(borrower);

        borrowerAttachMapper.insert(new BorrowerAttach()
                .setBorrowerId(borrower.getId())
                .setImageType("idCard1")
                .setImageUrl(dto.getIdCardFrontUrl()));
        borrowerAttachMapper.insert(new BorrowerAttach()
                .setBorrowerId(borrower.getId())
                .setImageType("idCard2")
                .setImageUrl(dto.getIdCardBackUrl()));

        user.setName(dto.getName());
        user.setIdCard(dto.getIdCard());
        user.setBorrowAuthStatus(BorrowerAuthStatusEnum.AUTHING.getCode());
        userService.updateById(user);

        return borrower.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveAuth(BorrowerApprovalDTO dto) {
        Long borrowerId = dto.getBorrowerId();
        Borrower borrower = getById(borrowerId);
        if (borrower == null) {
            throw new ResourceNotFoundException(404, "认证记录不存在");
        }
        if (!BorrowerAuthStatusEnum.AUTHING.getCode().equals(borrower.getStatus())) {
            throw new BusinessException(400, "仅认证中状态可审核");
        }
        boolean approved = Integer.valueOf(2).equals(dto.getStatus());
        borrower.setStatus(approved
                ? BorrowerAuthStatusEnum.AUTHED.getCode()
                : BorrowerAuthStatusEnum.AUTH_FAIL.getCode());
        updateById(borrower);

        User user = userService.getById(borrower.getUserId());
        if (approved) {
            user.setBorrowAuthStatus(BorrowerAuthStatusEnum.AUTHED.getCode());
            // 注意：不要覆盖 user_type！user_type 是单一标志位，
            // 改成 BORROWER 后投资人将无法投标（LendItemServiceImpl 要求 INVESTOR）
        } else {
            user.setBorrowAuthStatus(BorrowerAuthStatusEnum.AUTH_FAIL.getCode());
        }
        userService.updateById(user);

        if (approved) {
            int points = dto.getInfoIntegral() == null ? 0 : dto.getInfoIntegral();
            if (Boolean.TRUE.equals(dto.getIsIdCardOk())) points += 20;
            if (Boolean.TRUE.equals(dto.getIsHouseOk())) points += 30;
            if (Boolean.TRUE.equals(dto.getIsCarOk())) points += 20;
            if (points > 0) {
                userIntegralService.addIntegral(borrower.getUserId(), points, "实名认证审批加分");
            }
        }
        // 实名认证审批结果通知（通过/拒绝都通知）
        messageService.sendBorrowerAuthNotice(borrower.getUserId(), approved);
    }

    @Override
    public Borrower getByUserId(Long userId) {
        return getOne(new LambdaQueryWrapper<Borrower>().eq(Borrower::getUserId, userId));
    }

    @Override
    public Map<String, Object> getAuthStatus(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("borrowAuthStatus", user.getBorrowAuthStatus());
        data.put("borrower", getByUserId(userId));
        return data;
    }

    @Override
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Borrower> adminPageList(
            Integer pageNum, Integer pageSize, String keyword, Integer auditStatus) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Borrower> pageParam =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Borrower> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(keyword != null && !keyword.isEmpty(), Borrower::getName, keyword);
        wrapper.like(keyword != null && !keyword.isEmpty(), Borrower::getMobile, keyword);
        wrapper.like(keyword != null && !keyword.isEmpty(), Borrower::getIdCard, keyword);
        wrapper.eq(auditStatus != null, Borrower::getStatus, auditStatus);
        wrapper.orderByDesc(Borrower::getCreateTime);
        return this.page(pageParam, wrapper);
    }
}