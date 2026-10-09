package com.example.demo.controller.api;

import com.example.demo.service.LendItemService;
import com.example.demo.service.LendReturnService;
import com.example.demo.service.TransFlowService;
import com.example.demo.service.UserBindService;
import com.example.demo.utils.BankTradeLog;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 银行异步回调 Controller
 *
 * 分层约定：Controller 只做「接参 → 调 Service → 应答银行」，
 * 验签、字段校验、幂等、落库等业务逻辑全部在 Service 层。
 *
 * 应答约定（接口文档 5.3）：
 *   "success" —— 业务已受理（含验签通过处理成功、银行状态非成功、重复回调）
 *   "fail"    —— 验签失败或数据异常，银行会重试
 *
 * 对账约定：6 个回调统一经 {@link #respond} 出口，每笔回调整理成一条 BANK_TRADE 流水
 * （原始参数 + 受理结果 + 失败原因），落到 logs/bank-trade/bank-trade.log。
 * 之所以在此处而不是在 Service 内记录：Service 的失败分支目前是静默 return false，
 * 从日志分不清"验签失败""找不到用户"还是"重复回调"，统一在入口记录原始参数才能与银行对账。
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
        return respond("BIND", requestParams, userBindService::handleBindNotify);
    }

    @ApiOperation("充值异步回调")
    @PostMapping("/userAccount/notify")
    public String rechargeNotify(@RequestParam Map<String, String> requestParams) {
        return respond("RECHARGE", requestParams, transFlowService::handleRechargeNotify);
    }

    @ApiOperation("提现异步回调")
    @PostMapping("/userAccount/notifyWithdraw")
    public String withdrawNotify(@RequestParam Map<String, String> requestParams) {
        return respond("WITHDRAW", requestParams, transFlowService::handleWithdrawNotify);
    }

    @ApiOperation("投标异步回调")
    @PostMapping("/lendItem/notify")
    public String investNotify(@RequestParam Map<String, String> requestParams) {
        return respond("INVEST", requestParams, lendItemService::handleInvestNotify);
    }

    @ApiOperation("放款异步回调")
    @PostMapping("/lend/notifyMakeLoan")
    public String makeLoanNotify(@RequestParam Map<String, String> requestParams) {
        return respond("MAKE_LOAN", requestParams, lendReturnService::handleMakeLoanNotify);
    }

    @ApiOperation("还款异步回调")
    @PostMapping("/lendReturn/notifyUrl")
    public String repayNotify(@RequestParam Map<String, String> requestParams) {
        return respond("REPAY", requestParams, lendReturnService::handleRepayNotify);
    }

    /**
     * 回调统一出口：记录对账流水 → 调用业务处理 → 应答银行。
     *
     * <p>异常不吞：先落一条 ERROR 级对账流水（含异常类型与消息），再原样抛出，
     * 由全局异常处理返回 500，银行据此重试。这样"回调没生效"永远能在日志里查到原因。</p>
     */
    private String respond(String bizType, Map<String, String> requestParams,
                           Function<Map<String, Object>, Boolean> handler) {
        Map<String, Object> params = new HashMap<>(requestParams);
        boolean ok;
        try {
            ok = Boolean.TRUE.equals(handler.apply(params));
        } catch (RuntimeException e) {
            BankTradeLog.callbackError(bizType, params, e);
            throw e;
        }
        BankTradeLog.callback(bizType, params, ok,
                ok ? "已受理" : "未受理（验签失败 / 数据异常 / 找不到用户），等待银行重试");
        return ok ? "success" : "fail";
    }
}
