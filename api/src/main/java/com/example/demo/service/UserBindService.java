package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.UserBind;

import java.util.Map;

public interface UserBindService extends IService<UserBind> {

    UserBind getByUserId(Long userId);

    boolean isBind(String idCard);

    UserBind bind(UserBind userBind);

    void updateBindStatus(Long userId, Integer status, String bindCode);

    /**
     * 处理银行绑卡异步回调：验签 → 查用户 → 组装绑卡信息并落库
     *
     * @param params 银行回调的全部参数（含 sign）
     * @return true=业务已受理（应答 success）；false=验签失败或用户不存在（应答 fail）
     */
    boolean handleBindNotify(Map<String, Object> params);

    /**
     * 发起绑卡：校验实名状态/短信验证码 → 组装旺旺银行绑卡表单
     *
     * @param userId 用户id
     * @param code   短信验证码
     * @return 跳转到银行页面的 HTML 表单串
     */
    String buildBindForm(Long userId, String code);
}