package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.Borrower;
import com.example.demo.entity.BorrowerAttach;
import com.example.demo.entity.User;
import com.example.demo.entity.dto.BorrowerAuthDto;
import com.example.demo.enums.BorrowerAuthStatusEnum;
import com.example.demo.enums.UserTypeEnum;
import com.example.demo.mapper.BorrowerAttachMapper;
import com.example.demo.mapper.BorrowerMapper;
import com.example.demo.service.BorrowerService;
import com.example.demo.service.UserService;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitAuth(Long userId, BorrowerAuthDto dto) {
        User user = userService.getById(userId);
        // 建借款人档案（认证中）
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

        // 身份证正反面附件
        borrowerAttachMapper.insert(new BorrowerAttach()
                .setBorrowerId(borrower.getId())
                .setImageType("idCard1")
                .setImageUrl(dto.getIdCardFrontUrl()));
        borrowerAttachMapper.insert(new BorrowerAttach()
                .setBorrowerId(borrower.getId())
                .setImageType("idCard2")
                .setImageUrl(dto.getIdCardBackUrl()));

        // 同步 user 表：真实姓名、身份证号、认证状态=认证中
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
            throw new RuntimeException("认证记录不存在");
        }
        if (!BorrowerAuthStatusEnum.AUTHING.getCode().equals(borrower.getStatus())) {
            throw new RuntimeException("仅认证中状态可审核");
        }
        boolean approved = Integer.valueOf(2).equals(dto.getStatus());
        borrower.setStatus(approved
                ? BorrowerAuthStatusEnum.AUTHED.getCode()
                : BorrowerAuthStatusEnum.AUTH_FAIL.getCode());
        updateById(borrower);

        User user = userService.getById(borrower.getUserId());
        if (approved) {
            user.setBorrowAuthStatus(BorrowerAuthStatusEnum.AUTHED.getCode());
            user.setUserType(UserTypeEnum.BORROWER.getCode());
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
    }

    @Override
    public Borrower getByUserId(Long userId) {
        return getOne(new LambdaQueryWrapper<Borrower>().eq(Borrower::getUserId, userId));
    }

    @Override
    public Map<String, Object> getAuthStatus(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("borrowAuthStatus", user.getBorrowAuthStatus());
        data.put("borrower", getByUserId(userId));
        return data;
    }
}
