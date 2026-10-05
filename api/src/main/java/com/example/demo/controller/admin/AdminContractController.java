package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.Contract;
import com.example.demo.entity.ContractSignRecord;
import com.example.demo.mapper.ContractSignRecordMapper;
import com.example.demo.service.ContractService;
import com.example.demo.utils.ContractHashUtils;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.demo.utils.PageUtils;

/**
 * 后台合同台账（只读 + 作废，不含签署）
 */
@Slf4j
@Api(tags = "后台合同台账")
@RestController
@RequestMapping("/admin/core/contract")
public class AdminContractController {

    @Autowired
    private ContractService contractService;

    @Autowired
    private ContractSignRecordMapper contractSignRecordMapper;

    @ApiOperation("合同台账分页")
    @GetMapping("/page")
    public PccAjaxResult page(
            @ApiParam("合同类型 LOAN/INVEST/PAYMENT") @RequestParam(required = false) String contractType,
            @ApiParam("业务类型 LEND/BORROW_INFO/LEND_ITEM") @RequestParam(required = false) String bizType,
            @ApiParam("状态 0待签署 1已签署 2已作废") @RequestParam(required = false) Integer status,
            @ApiParam("合同编号 / 甲方姓名 / 乙方姓名 模糊") @RequestParam(required = false) String keyword,
            @ApiParam("当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页记录数") @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            Page<Contract> page = PageUtils.of(pageNum, pageSize);
            LambdaQueryWrapper<Contract> w = new LambdaQueryWrapper<>();
            w.eq(StringUtils.hasText(contractType), Contract::getContractType, contractType);
            w.eq(StringUtils.hasText(bizType), Contract::getBizType, bizType);
            w.eq(status != null, Contract::getStatus, status);
            if (StringUtils.hasText(keyword)) {
                String kw = keyword.trim();
                w.and(x -> x.like(Contract::getContractNo, kw)
                        .or().like(Contract::getPartyAName, kw)
                        .or().like(Contract::getPartyBName, kw));
            }
            w.orderByDesc(Contract::getCreateTime);
            Page<Contract> result = contractService.page(page, w);

            Map<String, Object> data = new HashMap<>();
            data.put("list", result.getRecords());
            data.put("total", result.getTotal());
            return new PccAjaxResult(200, "获取合同列表成功", data);
        } catch (Exception e) {
            log.error("查询合同台账失败: {}", e.getMessage(), e);
            return new PccAjaxResult(500, "查询合同列表失败：" + e.getMessage());
        }
    }

    @ApiOperation("合同详情 + 签署存证（含哈希校验结果）")
    @GetMapping("/{id}")
    public PccAjaxResult show(@PathVariable Long id) {
        try {
            // 管理端不限制查看人，传 null 跳过归属校验
            Map<String, Object> data = contractService.detail(id, null);
            Contract c = (Contract) data.get("contract");
            List<ContractSignRecord> records = contractSignRecordMapper.selectList(
                    new LambdaQueryWrapper<ContractSignRecord>()
                            .eq(ContractSignRecord::getContractId, id)
                            .orderByAsc(ContractSignRecord::getId));
            data.put("signRecords", records);

            // 逐条存证重算哈希，给出"哪个签署环节的正文被改过"的结论
            List<Map<String, Object>> audits = new ArrayList<>();
            boolean allValid = true;
            for (ContractSignRecord r : records) {
                Map<String, Object> m = new HashMap<>();
                boolean ok = r.getContentHash() != null
                        && r.getContentHash().equals(ContractHashUtils.sha256(c.getContentSnapshot()));
                m.put("signerId", r.getSignerId());
                m.put("signerName", r.getSignerName());
                m.put("signTime", r.getSignTime());
                m.put("valid", ok);
                audits.add(m);
                allValid = allValid && ok;
            }
            data.put("signAudits", audits);
            data.put("tamperFree", allValid);
            return new PccAjaxResult(200, "获取合同详情成功", data);
        } catch (ResourceNotFoundException e) {
            return new PccAjaxResult(e.getCode(), e.getMsg());
        }
    }

    @ApiOperation("作废合同（仅待签署可作废）")
    @PostMapping("/{id}/revoke")
    public PccAjaxResult revoke(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Object reason = body == null ? null : body.get("reason");
            contractService.revoke(id, reason == null ? null : reason.toString());
            return new PccAjaxResult(200, "作废成功", null);
        } catch (ResourceNotFoundException e) {
            return new PccAjaxResult(e.getCode(), e.getMsg());
        } catch (Exception e) {
            log.error("作废合同失败: id={}, error={}", id, e.getMessage(), e);
            return new PccAjaxResult(500, "作废失败：" + e.getMessage());
        }
    }

    /** 与 ContractServiceImpl 保持同一套 SHA-256 口径 */
}
