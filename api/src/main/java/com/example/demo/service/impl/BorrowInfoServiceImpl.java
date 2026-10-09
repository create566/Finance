package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.BorrowInfo;
import com.example.demo.entity.Lend;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.entity.dto.BorrowInfoDto;
import com.example.demo.enums.BindStatusEnum;
import com.example.demo.enums.BorrowInfoStatusEnum;
import com.example.demo.enums.BorrowerAuthStatusEnum;
import com.example.demo.enums.UserTypeEnum;
import com.example.demo.mapper.BorrowInfoMapper;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.example.demo.service.BorrowInfoService;
import com.example.demo.service.IntegralGradeService;
import com.example.demo.service.LendService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.demo.utils.PageUtils;

@Service
public class BorrowInfoServiceImpl extends ServiceImpl<BorrowInfoMapper, BorrowInfo> implements BorrowInfoService {

    @Autowired
    private UserService userService;

    @Autowired
    private UserBindService userBindService;

    @Autowired
    private IntegralGradeService integralGradeService;

    @Autowired
    private LendService lendService;

    @Autowired
    private com.example.demo.service.MessageService messageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BorrowInfo submitBorrowInfo(Long userId, BorrowInfoDto dto) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(500, "用户不存在");
        }

        boolean isDraft = dto.getIsDraft() != null && dto.getIsDraft();

        if (!isDraft) {
            // 身份校验：完成借款人实名认证即可申请借款。
            // 注意不能要求 user_type == BORROWER：实名认证通过后不再覆盖 user_type
            // （否则投资人被"降级"成借款人后将无法投标，形成死循环）
            if (!BorrowerAuthStatusEnum.AUTHED.getCode().equals(user.getBorrowAuthStatus())) {
                throw new ResourceNotFoundException(500, "请先完成借款人实名认证");
            }
            UserBind userBind = userBindService.getOne(new LambdaQueryWrapper<UserBind>()
                    .eq(UserBind::getUserId, userId)
                    .eq(UserBind::getStatus, BindStatusEnum.BOUND.getCode()));
            if (userBind == null) {
                throw new ResourceNotFoundException(500, "请先完成银行卡绑定");
            }
            BorrowInfo pending = this.getOne(new LambdaQueryWrapper<BorrowInfo>()
                    .eq(BorrowInfo::getUserId, userId)
                    .eq(BorrowInfo::getStatus, 1));
            if (pending != null) {
                throw new ResourceNotFoundException(500, "您有审核中的借款申请，请等待审核结果");
            }
            // 额度校验：申请金额不得超过当前积分对应的可借额度
            BigDecimal maxAmount = integralGradeService.getBorrowAmountByIntegral(user.getIntegral());
            if (dto.getAmount() == null || dto.getAmount().compareTo(maxAmount) > 0) {
                throw new ResourceNotFoundException(500, "借款金额超过可借额度：" + maxAmount);
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
    public boolean approveBorrowInfo(Long borrowInfoId, boolean approved, Long adminId) {
        BorrowInfo borrowInfo = this.getById(borrowInfoId);
        if (borrowInfo == null) {
            throw new ResourceNotFoundException(500, "借款申请不存在");
        }
        if (!BorrowInfoStatusEnum.AUDITING.getCode().equals(borrowInfo.getStatus())) {
            throw new ResourceNotFoundException(500, "只有审核中的申请才能审批");
        }

        // 带状态条件的原子更新：两个管理员并发点"通过"时只有一个能更新成功
        boolean updated = this.update(new LambdaUpdateWrapper<BorrowInfo>()
                .eq(BorrowInfo::getId, borrowInfoId)
                .eq(BorrowInfo::getStatus, BorrowInfoStatusEnum.AUDITING.getCode())
                .set(BorrowInfo::getStatus, approved
                        ? BorrowInfoStatusEnum.APPROVED.getCode()
                        : BorrowInfoStatusEnum.REJECTED.getCode())
                // 审核留痕：谁批的、什么时候批的（此前 borrow_info 连这两个字段都没有）
                .set(BorrowInfo::getCheckAdminId, adminId)
                .set(BorrowInfo::getCheckTime, LocalDateTime.now())
                .set(BorrowInfo::getUpdateTime, LocalDateTime.now()));
        if (!updated) {
            log.warn("借款申请已被其他管理员处理，borrowInfoId=" + borrowInfoId);
            return false;
        }
        if (approved) {
            // 防重复建标：一个借款申请只能生成一个标的
            long exists = lendService.count(new LambdaQueryWrapper<Lend>()
                    .eq(Lend::getBorrowInfoId, borrowInfoId));
            if (exists == 0) {
                lendService.createLend(borrowInfo);
            } else {
                log.warn("借款申请已生成过标的，跳过重复建标，borrowInfoId=" + borrowInfoId);
            }
        }
        try {
            messageService.sendBorrowApprovalNotice(borrowInfo.getUserId(), approved, borrowInfo.getAmount());
        } catch (Exception e) {
            log.error("发送审批通知失败, borrowInfoId=" + borrowInfoId + ", error=" + e.getMessage(), e);
        }
        return updated;
    }

    @Override
    public boolean deleteDraft(Long userId) {
        return this.remove(new LambdaQueryWrapper<BorrowInfo>()
                .eq(BorrowInfo::getUserId, userId)
                .eq(BorrowInfo::getStatus, BorrowInfoStatusEnum.DRAFT.getCode()));
    }

    @Override
    public Map<String, Object> getStatusByUser(Long userId) {
        Map<String, Object> data = new HashMap<>();
        BorrowInfo latest = getLatestByUser(userId);
        data.put("hasApply", latest != null);
        if (latest != null) {
            data.put("borrowInfo", latest);
        }
        return data;
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

    @Override
    public Page<BorrowInfo> adminPageList(Integer pageNum, Integer pageSize, String keyword, Integer status) {
        Page<BorrowInfo> pageParam = PageUtils.of(pageNum, pageSize);
        LambdaQueryWrapper<BorrowInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, BorrowInfo::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 只做能精确下推到 SQL 的条件：用户ID 数字匹配 + 金额字符串匹配。
            // 借款申请表没有姓名字段（姓名在 borrower/user 表），此处不跨表，避免误导性空结果。
            wrapper.and(w -> {
                w.like(BorrowInfo::getAmount, kw);
                try {
                    w.or().eq(BorrowInfo::getUserId, Long.valueOf(kw));
                } catch (NumberFormatException ignored) {
                    // 非数字关键词就只按金额模糊匹配
                }
            });
        }
        wrapper.orderByDesc(BorrowInfo::getCreateTime);
        return this.page(pageParam, wrapper);
    }

    /**
     * 管理端「审核」入口。
     *
     * <p>⚠️ 本方法**必须自带 {@code @Transactional}**：方法体内是
     * {@code this.approveBorrowInfo(...)} 的同类自调用，Spring 事务代理不会介入，
     * 内层 {@code approveBorrowInfo} 上的注解**形同虚设**。</p>
     *
     * <p>不加的后果：审核通过后 {@code createLend()} 建标或发通知抛异常时，
     * 借款状态已被独立事务提交为"已通过"，留下"审核通过但没有对应标的"的悬挂借款；
     * 而重审会被 {@code approveBorrowInfo} 的状态校验直接拒绝，无法自愈。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean auditBorrowInfo(Long id, Integer status, Long adminId) {
        boolean approved = BorrowInfoStatusEnum.APPROVED.getCode().equals(status);
        return this.approveBorrowInfo(id, approved, adminId);
    }

}