package com.example.demo.controller.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.Contract;
import com.example.demo.enums.ContractBizTypeEnum;
import com.example.demo.enums.ContractTypeEnum;
import com.example.demo.service.ContractService;
import com.wwfinance.common.utils.LoginUserContext;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户端电子合同接口
 * <p>
 * 合同生命周期：prepare（取/生成合同）→ detail（看正文）→ sign（手写签名）。
 * 资金动作（发布/投资/放款）在 Service 层用 assertSigned 做强校验。
 */
@Slf4j
@Api(tags = "电子合同")
@RestController
@RequestMapping("/api/core/contract")
public class ContractController {

    @Autowired
    private ContractService contractService;

    @ApiOperation("我的合同列表")
    @GetMapping("/auth/list")
    public PccAjaxResult list(
            @ApiParam("合同类型 LOAN/INVEST/PAYMENT/SERVICE/DEPOSIT") @RequestParam(required = false) String contractType,
            @ApiParam("状态 0待签署 1已签署 2已作废") @RequestParam(required = false) Integer status,
            @ApiParam("当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页记录数") @RequestParam(defaultValue = "10") Integer pageSize) {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        Page<Contract> page = contractService.pageMy(userId.longValue(), contractType, status, pageNum, pageSize);
        Map<String, Object> data = new HashMap<>();
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        return new PccAjaxResult(200, "获取我的合同成功", data);
    }

    @ApiOperation("获取或生成合同（投资前发现未签署时调用）")
    @PostMapping("/auth/prepare")
    public PccAjaxResult prepare(@RequestBody Map<String, Object> body) {
        Integer userIdInt = LoginUserContext.getUserid();
        if (userIdInt == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        Long userId = userIdInt.longValue();
        try {
            ContractBizTypeEnum bizType = ContractBizTypeEnum.fromCode(str(body.get("bizType")));
            ContractTypeEnum type = ContractTypeEnum.fromCode(str(body.get("contractType")));
            if (bizType == null || type == null) {
                return new PccAjaxResult(400, "参数错误：bizType 与 contractType 必填且合法");
            }
            if (body.get("bizId") == null) {
                return new PccAjaxResult(400, "参数错误：bizId 不能为空");
            }
            Long bizId = Long.valueOf(body.get("bizId").toString());
            BigDecimal amount = body.get("amount") == null ? null : new BigDecimal(body.get("amount").toString());

            // 出借协议以当前登录投资人为主体；借款/放款合同主体是借款人，由 Service 按标的主键归属
            Contract c = contractService.getOrCreate(bizType, bizId, type, userId, amount);
            if (!userId.equals(c.getPartyAId()) && !userId.equals(c.getPartyBId())) {
                return new PccAjaxResult(403, "您不是该合同的签署方");
            }
            Map<String, Object> data = new HashMap<>();
            data.put("contract", c);
            data.put("content", c.getContentSnapshot());
            data.put("signed", c.getStatus() != null && c.getStatus() == 1);
            return new PccAjaxResult(200, "获取合同成功", data);
        } catch (ResourceNotFoundException e) {
            return new PccAjaxResult(500, e.getMsg());
        } catch (Exception e) {
            log.error("获取合同失败: userId={}, error={}", userId, e.getMessage(), e);
            return new PccAjaxResult(500, "获取合同失败：" + e.getMessage());
        }
    }

    @ApiOperation("合同详情（正文 + 签署存证 + 哈希校验）")
    @GetMapping("/auth/detail/{contractId}")
    public PccAjaxResult detail(@PathVariable Long contractId) {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        try {
            return new PccAjaxResult(200, "获取合同详情成功",
                    contractService.detail(contractId, userId.longValue()));
        } catch (ResourceNotFoundException e) {
            return new PccAjaxResult(e.getCode(), e.getMsg());
        }
    }

    @ApiOperation("导出合同正文（返回 fileName + content，由前端生成下载文件）")
    @GetMapping("/auth/export/{contractId}")
    public PccAjaxResult export(@PathVariable Long contractId) {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        try {
            return new PccAjaxResult(200, "导出成功",
                    contractService.exportFile(contractId, userId.longValue()));
        } catch (ResourceNotFoundException e) {
            return new PccAjaxResult(e.getCode(), e.getMsg());
        }
    }

    @ApiOperation("补建历史缺失的待签合同（合同模块上线前的标的/投资记录没有合同）")
    @PostMapping("/auth/sync")
    public PccAjaxResult sync() {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        try {
            return new PccAjaxResult(200, "同步完成", contractService.syncMyContracts(userId.longValue()));
        } catch (Exception e) {
            log.error("合同同步失败: userId={}, error={}", userId, e.getMessage(), e);
            return new PccAjaxResult(500, "同步失败：" + e.getMessage());
        }
    }

    @ApiOperation("提交手写签名")
    @PostMapping("/auth/sign")
    public PccAjaxResult sign(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        try {
            if (body.get("contractId") == null) {
                return new PccAjaxResult(400, "参数错误：contractId 不能为空");
            }
            Long contractId = Long.valueOf(body.get("contractId").toString());
            String img = str(body.get("signImgBase64"));
            Contract c = contractService.sign(contractId, userId.longValue(), img,
                    clientIp(request), truncate(request.getHeader("User-Agent"), 250));
            Map<String, Object> data = new HashMap<>();
            data.put("contractNo", c.getContractNo());
            data.put("status", c.getStatus());
            return new PccAjaxResult(200, "签署成功", data);
        } catch (ResourceNotFoundException e) {
            return new PccAjaxResult(e.getCode(), e.getMsg());
        } catch (Exception e) {
            log.error("合同签署失败: userId={}, error={}", userId, e.getMessage(), e);
            return new PccAjaxResult(500, "签署失败：" + e.getMessage());
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String truncate(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** 取真实客户端 IP：经过网关/代理时优先读 X-Forwarded-For 的第一段 */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff) && !"unknown".equalsIgnoreCase(xff)) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(real) && !"unknown".equalsIgnoreCase(real)) {
            return real;
        }
        return request.getRemoteAddr();
    }
}
