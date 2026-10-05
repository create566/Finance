package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.Bank.FormHelper;
import com.example.demo.Bank.HfbConst;
import com.example.demo.client.SMSApiSmsClient;
import com.example.demo.entity.UserBind;
import com.example.demo.entity.User;
import com.example.demo.enums.BorrowerAuthStatusEnum;
import com.example.demo.enums.InvestorAuthStatusEnum;
import com.example.demo.exception.BusinessException;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.example.demo.mapper.UserBindMapper;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.wwfinance.common.utils.BankSignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class UserBindServiceImpl extends ServiceImpl<UserBindMapper, UserBind> implements UserBindService {

    @Autowired
    private UserBindMapper userBindMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private SMSApiSmsClient smsApiSmsClient;

    @Autowired
    private com.example.demo.service.MessageService messageService;

    @Override
    public UserBind getByUserId(Long userId) {
        LambdaQueryWrapper<UserBind> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBind::getUserId, userId);
        queryWrapper.last("LIMIT 1");
        return userBindMapper.selectOne(queryWrapper);
    }

    @Override
    public UserBind getByBindCode(String bindCode) {
        if (bindCode == null || bindCode.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<UserBind> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBind::getBindCode, bindCode);
        queryWrapper.last("LIMIT 1");
        return userBindMapper.selectOne(queryWrapper);
    }

    @Override
    public boolean isBind(String idCard) {
        LambdaQueryWrapper<UserBind> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBind::getIdCard, idCard);
        queryWrapper.eq(UserBind::getStatus, 1);
        return userBindMapper.selectCount(queryWrapper) > 0;
    }

    /**
     * 绑卡回调处理（Controller 只负责接参和应答，业务全在本方法）
     * 流程：验签 → 查用户 → 组装绑卡信息 → 落库并同步用户绑卡状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleBindNotify(Map<String, Object> params) {
        if (!BankSignUtil.isSignEquals(params)) {
            log.warn("绑卡回调验签失败，params={}", params);
            return false;
        }
        String bindCode = (String) params.get("bindCode");
        String idCard = (String) params.get("idCard");
        String bankNo = (String) params.get("bankNo");
        String name = (String) params.get("personalName");
        String bankType = (String) params.get("bankType");
        String agentUserId = (String) params.get("agentUserId");
        String status = (String) params.get("status");

        // 必填参数校验：银行回调必须携带 bindCode 和 idCard
        if (bindCode == null || bindCode.isEmpty() || idCard == null || idCard.isEmpty()) {
            log.warn("绑卡回调缺少必填参数，bindCode={}, idCard={}", bindCode, idCard);
            return false;
        }

        User user = userService.getByMobile(agentUserId);
        if (user == null) {
            log.warn("绑卡回调用户不存在，agentUserId={}", agentUserId);
            return false;
        }

        UserBind userBind = new UserBind();
        userBind.setUserId(user.getId());
        userBind.setBindCode(bindCode);
        userBind.setIdCard(idCard);
        userBind.setBankNo(bankNo);
        userBind.setName(name != null ? name : user.getName());
        userBind.setBankType(bankType);
        userBind.setMobile(user.getMobile());
        userBind.setStatus("1".equals(status) ? 1 : 0);
        bind(userBind);
        // 绑卡成功通知（仅银行侧成功时发送）
        if ("1".equals(status)) {
            messageService.sendBindNotice(user.getId(), bankNo);
        }
        return true;
    }

    /**
     * 发起绑卡（Controller 只负责接参和返回表单，校验/拼装全在本方法）
     * 流程：查用户 → 校验实名状态 → 校验短信验证码 → 组装银行参数+签名+表单
     */
    @Override
    public String buildBindForm(Long userId, String code) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "用户不存在");
        }
        // 前置校验1：必须先完成实名认证（借款人或投资人任一通过即可）
        boolean borrowerAuthed = BorrowerAuthStatusEnum.AUTHED.getCode().equals(user.getBorrowAuthStatus());
        boolean investorAuthed = InvestorAuthStatusEnum.AUTHED.getCode().equals(user.getInvestAuthStatus());
        if (!borrowerAuthed && !investorAuthed) {
            throw new BusinessException(400, "请先完成实名认证");
        }
        // 前置校验2：短信验证码（Feign 调用容错）
        try {
            if (code == null || !Boolean.TRUE.equals(smsApiSmsClient.checkCode(user.getMobile(), code).get("data"))) {
                throw new BusinessException(400, "验证码错误或已过期");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("短信验证码服务调用失败，mobile=" + user.getMobile() + ", error=" + e.getMessage());
            throw new BusinessException(500, "短信服务不可用，请稍后再试");
        }

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", HfbConst.AGENT_ID);
        paramMap.put("agentUserId", user.getMobile());
        paramMap.put("personalName", user.getName());
        paramMap.put("mobile", user.getMobile());
        paramMap.put("idCard", user.getIdCard());
        paramMap.put("returnUrl", HfbConst.USERBIND_RETURN_URL);
        paramMap.put("notifyUrl", HfbConst.USERBIND_NOTIFY_URL);
        paramMap.put("timestamp", new Date().getTime());
        paramMap.put("sign", BankSignUtil.getSign(paramMap));

        return FormHelper.buildForm(HfbConst.USERBIND_URL, paramMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserBind bind(UserBind userBind) {
        // 已绑定过：更新字段（防银行重复回调撞唯一键）
        UserBind exist = getByUserId(userBind.getUserId());
        if (exist != null) {
            // 银行失败回调不应覆盖已成功的绑卡记录
            if (exist.getStatus() != null && exist.getStatus() == 1 && userBind.getStatus() != 1) {
                log.warn("已存在成功绑卡记录，忽略失败回调，userId={}", userBind.getUserId());
                return exist;
            }
            exist.setBindCode(userBind.getBindCode());
            exist.setIdCard(userBind.getIdCard());
            exist.setBankNo(userBind.getBankNo());
            if (userBind.getName() != null) {
                exist.setName(userBind.getName());
            }
            if (userBind.getBankType() != null) {
                exist.setBankType(userBind.getBankType());
            }
            if (userBind.getMobile() != null) {
                exist.setMobile(userBind.getMobile());
            }
            exist.setStatus(userBind.getStatus());
            userBindMapper.updateById(exist);
        } else {
            userBindMapper.insert(userBind);
        }
        // 同步用户绑卡状态：与绑卡记录保持一致（银行回调失败时不会误标为已绑）
        User user = userService.getById(userBind.getUserId());
        if (user != null) {
            user.setBindStatus(userBind.getStatus());
            user.setBindCode(userBind.getBindCode());
            userService.updateById(user);
        }
        return userBind;
    }

    @Override
    public void updateBindStatus(Long userId, Integer status, String bindCode) {
        UserBind userBind = getByUserId(userId);
        if (userBind != null) {
            userBind.setStatus(status);
            userBind.setBindCode(bindCode);
            userBindMapper.updateById(userBind);
            User user = userService.getById(userId);
            if (user != null) {
                user.setBindStatus(status);
                user.setBindCode(bindCode);
                userService.updateById(user);
            }
        }
    }
}