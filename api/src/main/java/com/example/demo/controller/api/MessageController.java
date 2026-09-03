package com.example.demo.controller.api;


import com.example.demo.client.SMSApiSmsClient;
import com.wwfinance.common.result.PccAjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessageController {

    @Autowired
    private SMSApiSmsClient sMSApiSmsClient;
    @GetMapping("/api/sendtest")
    public PccAjaxResult sendsms(){
      String msg =   sMSApiSmsClient.sendMsg("222222");
        System.out.println("test");
        System.out.println(msg);
        return new PccAjaxResult(200, msg);
    }
}
