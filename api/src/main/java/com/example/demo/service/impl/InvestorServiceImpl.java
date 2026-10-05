package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.Investor;
import com.example.demo.entity.InvestorAttach;
import com.example.demo.entity.User;
import com.example.demo.entity.dto.InvestorApprovalDTO;
import com.example.demo.entity.dto.InvestorAuthDto;
import com.example.demo.enums.InvestorAuthStatusEnum;
import com.example.demo.exception.BusinessException;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.example.demo.mapper.InvestorAttachMapper;
import com.example.demo.mapper.InvestorMapper;
import com.example.demo.service.InvestorService;
import com.example.demo.service.MessageService;
import com.example.demo.service.UserIntegralService;
import com.example.demo.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import com.example.demo.utils.PageUtils;

@Service
public class InvestorServiceImpl extends ServiceImpl<InvestorMapper, Investor> implements InvestorService {

    @Resource
    private InvestorAttachMapper investorAttachMapper;

    @Resource
    private UserService userService;
    @Resource
    private UserIntegralService userIntegralService;
    @Resource
    private MessageService messageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitAuth(Long userId, InvestorAuthDto dto) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "用户不存在");
        }

        LambdaQueryWrapper<Investor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Investor::getUserId, userId);
        Investor existingInvestor = getOne(wrapper);

        if (existingInvestor != null) {
            if (InvestorAuthStatusEnum.AUTHED.getCode().equals(existingInvestor.getStatus())) {
                throw new BusinessException(400, "已完成认证，无需重复提交");
            }
            if (InvestorAuthStatusEnum.AUTHING.getCode().equals(existingInvestor.getStatus())) {
                throw new BusinessException(400, "已提交认证，请等待审核");
            }
            existingInvestor.setName(dto.getName())
                    .setIdCard(dto.getIdCard())
                    .setMobile(user.getMobile())
                    .setSex(dto.getSex())
                    .setAge(dto.getAge())
                    .setStatus(InvestorAuthStatusEnum.AUTHING.getCode());
            updateById(existingInvestor);

            // 重新提交时清空旧附件，写入新附件
            LambdaQueryWrapper<InvestorAttach> attachWrapper = new LambdaQueryWrapper<>();
            attachWrapper.eq(InvestorAttach::getInvestorId, existingInvestor.getId());
            investorAttachMapper.delete(attachWrapper);
            saveAttach(existingInvestor.getId(), dto);

            user.setName(dto.getName());
            user.setIdCard(dto.getIdCard());
            user.setInvestAuthStatus(InvestorAuthStatusEnum.AUTHING.getCode());
            userService.updateById(user);

            return existingInvestor.getId();
        }

        Investor investor = new Investor()
                .setUserId(userId)
                .setName(dto.getName())
                .setIdCard(dto.getIdCard())
                .setMobile(user.getMobile())
                .setSex(dto.getSex())
                .setAge(dto.getAge())
                .setStatus(InvestorAuthStatusEnum.AUTHING.getCode());
        save(investor);
        saveAttach(investor.getId(), dto);

        user.setName(dto.getName());
        user.setIdCard(dto.getIdCard());
        user.setInvestAuthStatus(InvestorAuthStatusEnum.AUTHING.getCode());
        userService.updateById(user);

        return investor.getId();
    }

    /** 保存身份证正反面附件 */
    private void saveAttach(Long investorId, InvestorAuthDto dto) {
        investorAttachMapper.insert(new InvestorAttach()
                .setInvestorId(investorId)
                .setImageType("idCard1")
                .setImageUrl(dto.getIdCardFrontUrl()));
        investorAttachMapper.insert(new InvestorAttach()
                .setInvestorId(investorId)
                .setImageType("idCard2")
                .setImageUrl(dto.getIdCardBackUrl()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveAuth(InvestorApprovalDTO dto) {
        Long investorId = dto.getInvestorId();
        Investor investor = getById(investorId);
        if (investor == null) {
            throw new ResourceNotFoundException(404, "认证记录不存在");
        }
        if (!InvestorAuthStatusEnum.AUTHING.getCode().equals(investor.getStatus())) {
            throw new BusinessException(400, "仅认证中状态可审核");
        }
        boolean approved = Integer.valueOf(2).equals(dto.getStatus());
        investor.setStatus(approved
                ? InvestorAuthStatusEnum.AUTHED.getCode()
                : InvestorAuthStatusEnum.AUTH_FAIL.getCode());
        updateById(investor);

        User user = userService.getById(investor.getUserId());
        if (approved) {
            user.setInvestAuthStatus(InvestorAuthStatusEnum.AUTHED.getCode());
        } else {
            user.setInvestAuthStatus(InvestorAuthStatusEnum.AUTH_FAIL.getCode());
        }
        userService.updateById(user);

        // 认证通过加分（身份证 20 分）
        if (approved && Boolean.TRUE.equals(dto.getIsIdCardOk())) {
            userIntegralService.addIntegral(investor.getUserId(), 20, "投资人实名认证审批加分");
        }
        // 实名认证审批结果通知（通过/拒绝都通知，复用 BORROWER_AUTH 类型）
        messageService.sendInvestorAuthNotice(investor.getUserId(), approved);
    }

    @Override
    public Investor getByUserId(Long userId) {
        return getOne(new LambdaQueryWrapper<Investor>().eq(Investor::getUserId, userId));
    }

    @Override
    public Map<String, Object> getAuthStatus(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("investAuthStatus", user.getInvestAuthStatus());
        data.put("investor", getByUserId(userId));
        return data;
    }

    @Override
    public Page<Investor> adminPageList(Integer pageNum, Integer pageSize, String keyword, Integer auditStatus) {
        Page<Investor> pageParam = PageUtils.of(pageNum, pageSize);
        LambdaQueryWrapper<Investor> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(keyword != null && !keyword.isEmpty(), Investor::getName, keyword);
        wrapper.like(keyword != null && !keyword.isEmpty(), Investor::getMobile, keyword);
        wrapper.like(keyword != null && !keyword.isEmpty(), Investor::getIdCard, keyword);
        wrapper.eq(auditStatus != null, Investor::getStatus, auditStatus);
        wrapper.orderByDesc(Investor::getCreateTime);
        return this.page(pageParam, wrapper);
    }
}
