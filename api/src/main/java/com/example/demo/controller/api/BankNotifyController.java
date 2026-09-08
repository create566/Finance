package com.example.demo.controller.api;

import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.service.TransFlowService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.wwfinance.common.utils.BankSignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/core")
public class BankNotifyController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserBindService userBindService;

    @Autowired
    private TransFlowService transFlowService;

    /**
     * 把银行回调参数统一转成 Map<String, Object>（供验签使用）
     */
    private Map<String, Object> switchMap(Map<String, String> requestParams) {
        Map<String, Object> paramMap = new HashMap<>();
        for (Map.Entry<String, String> entry : requestParams.entrySet()) {
            paramMap.put(entry.getKey(), entry.getValue());
        }
        return paramMap;
    }

    @PostMapping("/userBind/notify")
    public String notify(@RequestParam Map<String, String> requestParams) {
        Map<String, Object> paramMap = switchMap(requestParams);
        if (!BankSignUtil.isSignEquals(paramMap)) {
            log.warn("绑卡回调验签失败，params={}", paramMap);
            return "fail";
        }

        String bindCode = (String) paramMap.get("bindCode");
        String idCard = (String) paramMap.get("idCard");
        String bankNo = (String) paramMap.get("bankNo");
        String name = (String) paramMap.get("personalName");
        String bankType = (String) paramMap.get("bankType");
        String agentUserId = (String) paramMap.get("agentUserId");
        String status = (String) paramMap.get("status");

        User user = userService.getByMobile(agentUserId);
        if (user == null) {
            log.warn("绑卡回调用户不存在，agentUserId={}", agentUserId);
            return "fail";
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
        userBindService.bind(userBind);

        return "success";
    }

    /**
     * 充值回调：银行扣款成功后异步通知平台，平台同步本地账本
     */
    @PostMapping("/userAccount/notify")
    public String rechargeNotify(@RequestParam Map<String, String> requestParams) {
        Map<String, Object> paramMap = switchMap(requestParams);
        if (!BankSignUtil.isSignEquals(paramMap)) {
            log.warn("充值回调验签失败，params={}", paramMap);
            return "fail";
        }
        String agentUserId = (String) paramMap.get("agentUserId");
        String amountStr = (String) paramMap.get("amount");
        String transNo = (String) paramMap.get("transNo");
        String status = (String) paramMap.get("status");
        if (!"1".equals(status)) {
            return "success";
        }
        if (amountStr == null || !amountStr.matches("\\d+(\\.\\d{1,2})?")) {
            log.warn("充值回调金额非法，amount={}", amountStr);
            return "fail";
        }
        User user = userService.getByMobile(agentUserId);
        if (user == null) {
            log.warn("充值回调用户不存在，agentUserId={}", agentUserId);
            return "fail";
        }
        // 幂等：同一银行流水号不重复入账
        if (transNo != null && transFlowService.existsByTransNo(transNo)) {
            return "success";
        }
        transFlowService.recharge(user.getId(), new BigDecimal(amountStr), transNo);
        return "success";
    }

    /**
     * 提现回调：银行打款成功后异步通知平台，平台同步本地账本
     */
    @PostMapping("/userAccount/notifyWithdraw")
    public String withdrawNotify(@RequestParam Map<String, String> requestParams) {
        Map<String, Object> paramMap = switchMap(requestParams);
        if (!BankSignUtil.isSignEquals(paramMap)) {
            log.warn("提现回调验签失败，params={}", paramMap);
            return "fail";
        }
        String agentUserId = (String) paramMap.get("agentUserId");
        String amountStr = (String) paramMap.get("amount");
        String transNo = (String) paramMap.get("transNo");
        String status = (String) paramMap.get("status");
        if (!"1".equals(status)) {
            return "success";
        }
        if (amountStr == null || !amountStr.matches("\\d+(\\.\\d{1,2})?")) {
            log.warn("提现回调金额非法，amount={}", amountStr);
            return "fail";
        }
        User user = userService.getByMobile(agentUserId);
        if (user == null) {
            log.warn("提现回调用户不存在，agentUserId={}", agentUserId);
            return "fail";
        }
        if (transNo != null && transFlowService.existsByTransNo(transNo)) {
            return "success";
        }
        transFlowService.withdraw(user.getId(), new BigDecimal(amountStr), transNo);
        return "success";
    }
}
