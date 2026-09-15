package com.example.demo.controller.api;

import com.example.demo.service.LendItemService;
import com.example.demo.service.LendReturnService;
import com.example.demo.service.TransFlowService;
import com.example.demo.service.UserBindService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 银行异步回调 Controller
 *
 * 分层约定：Controller 只做「接参 → 调 Service → 应答银行」，
 * 验签、字段校验、幂等、落库等业务逻辑全部在 Service 层。
 *
 * 应答约定（接口文档 5.3）：
 *   "success" —— 业务已受理（含验签通过处理成功、银行状态非成功、重复回调）
 *   "fail"    —— 验签失败或数据异常，银行会重试
 */
@Api(tags = "银行异步回调")
@RestController
@RequestMapping("/api/core")
public class BankNotifyController {

    @Autowired
    private UserBindService userBindService;

    @Autowired
    private TransFlowService transFlowService;

    @Autowired
    private LendItemService lendItemService;

    @Autowired
    private LendReturnService lendReturnService;

    @ApiOperation("绑卡异步回调")
    @PostMapping("/userBind/notify")
    public String notify(@RequestParam Map<String, String> requestParams) {
        return userBindService.handleBindNotify(new HashMap<>(requestParams)) ? "success" : "fail";
    }

    @ApiOperation("充值异步回调")
    @PostMapping("/userAccount/notify")
    public String rechargeNotify(@RequestParam Map<String, String> requestParams) {
        return transFlowService.handleRechargeNotify(new HashMap<>(requestParams)) ? "success" : "fail";
    }

    @ApiOperation("提现异步回调")
    @PostMapping("/userAccount/notifyWithdraw")
    public String withdrawNotify(@RequestParam Map<String, String> requestParams) {
        return transFlowService.handleWithdrawNotify(new HashMap<>(requestParams)) ? "success" : "fail";
    }

    @ApiOperation("投标异步回调")
    @PostMapping("/lendItem/notify")
    public String investNotify(@RequestParam Map<String, String> requestParams) {
        return lendItemService.handleInvestNotify(new HashMap<>(requestParams)) ? "success" : "fail";
    }

    @ApiOperation("放款异步回调")
    @PostMapping("/lend/notifyMakeLoan")
    public String makeLoanNotify(@RequestParam Map<String, String> requestParams) {
        return lendReturnService.handleMakeLoanNotify(new HashMap<>(requestParams)) ? "success" : "fail";
    }

    @ApiOperation("还款异步回调")
    @PostMapping("/lendReturn/notifyUrl")
    public String repayNotify(@RequestParam Map<String, String> requestParams) {
        return lendReturnService.handleRepayNotify(new HashMap<>(requestParams)) ? "success" : "fail";
    }
}
