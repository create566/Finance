package com.example.demo.controller.api;

import com.example.demo.entity.User;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import com.wwfinance.common.utils.BankSignUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/core")
public class BankNotifyController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserBindService userBindService;

    @PostMapping("/userBind/notify")
    public String notify(@RequestParam Map<String, String> requestParams) {
        Map<String, Object> paramMap = new HashMap<>();
        for (Map.Entry<String, String> entry : requestParams.entrySet()) {
            paramMap.put(entry.getKey(), entry.getValue());
        }
        if (!BankSignUtil.isSignEquals(paramMap)) {
            return "sign error";
        }

        String bindCode = (String) paramMap.get("bindCode");
        String idCard = (String) paramMap.get("idCard");
        String agentUserId = (String) paramMap.get("agentUserId");
        String status = (String) paramMap.get("status");

        User user = userService.getByMobile(agentUserId);
        if (user == null) {
            return "user not found";
        }

        com.example.demo.entity.UserBind userBind = new com.example.demo.entity.UserBind();
        userBind.setUserId(user.getId());
        userBind.setBindCode(bindCode);
        userBind.setIdCard(idCard);
        userBind.setStatus("1".equals(status) ? 1 : 0);
        userBindService.bind(userBind);

        return "success";
    }
}