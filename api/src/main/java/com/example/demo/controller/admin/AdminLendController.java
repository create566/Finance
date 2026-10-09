package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.entity.LendReturn;
import com.example.demo.entity.SysAdmin;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.mapper.SysAdminMapper;
import com.example.demo.service.LendItemService;
import com.example.demo.service.LendReturnService;
import com.example.demo.service.LendService;
import com.example.demo.utils.LendNoUtils;
import com.wwfinance.common.auth.AdminRoles;
import com.wwfinance.common.auth.RequireAdminRole;
import com.wwfinance.common.utils.LoginUserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台标的管理（对齐前端 /admin/core/loan/project/** 约定）
 * <p>
 * 功能清单：
 * 1. 标的CRUD（列表、创建、发布、下架）
 * 2. 投资管理（查看投资列表）
 * 3. 还款管理（还款计划、逾期扫描、满标放款）
 *
 * @author System
 * @version 2.0 (2026-09-22 优化版)
 */
@Api(tags = "后台标的管理")
@RestController
@RequestMapping("/admin/core/loan/project")
@Slf4j
public class AdminLendController {

    @Autowired
    private LendService lendService;

    @Autowired
    private LendItemService lendItemService;

    @Autowired
    private LendReturnService lendReturnService;

    @Autowired
    private com.example.demo.service.ContractService contractService;

    @Autowired
    private SysAdminMapper sysAdminMapper;

    /**
     * 用 Spring 容器里的 ObjectMapper（而非 new ObjectMapper()），
     * 它带 JavaTimeModule，LocalDateTime 的序列化格式与控制器直接返回实体时一致，
     * 前端既有的时间展示不会因为"改成手工转 Map"而变形。
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取标的分页列表
     * 支持关键词搜索（标的名称/编号）和状态筛选
     * <p>同时返回上架审核留痕（审核人姓名 + 审核时间）与放款发起人，供后台运营追溯。
     */
    @ApiOperation("获取标的分页列表")
    @GetMapping("/page")
    public PccAjaxResult page(
            @ApiParam("当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页记录数") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam("标的名称/编号模糊搜索") @RequestParam(required = false) String keyword,
            @ApiParam("状态:0待发布 1募资中 2已完成 3逾期 4下架") @RequestParam(required = false) Integer status) {
        try {
            Page<Lend> result = lendService.pageList(pageNum, pageSize, keyword, status);
            List<Lend> records = result.getRecords();

            // 批量反查管理员姓名（一次查询，避免 N+1）
            Set<Long> adminIds = new HashSet<>();
            for (Lend l : records) {
                if (l.getCheckAdminId() != null) {
                    adminIds.add(l.getCheckAdminId());
                }
                if (l.getPaymentAdminId() != null) {
                    adminIds.add(l.getPaymentAdminId());
                }
                if (l.getOfflineAdminId() != null) {
                    adminIds.add(l.getOfflineAdminId());
                }
            }
            Map<Long, String> adminNames = resolveAdminNames(adminIds);

            // Lend 的 check_admin_id / check_time / payment_admin_id 带 @JsonIgnore（不对前台暴露），
            // 这里转 Map 后显式回填，保持响应结构与前端既有字段路径完全一致（扁平，不嵌套）
            List<Map<String, Object>> list = new ArrayList<>(records.size());
            for (Lend l : records) {
                Map<String, Object> m = objectMapper.convertValue(
                        l, new TypeReference<LinkedHashMap<String, Object>>() {
                        });
                m.put("checkAdminId", l.getCheckAdminId());
                m.put("checkTime", l.getCheckTime());
                m.put("checkAdminName", displayName(l.getCheckAdminId(), adminNames));
                m.put("paymentAdminId", l.getPaymentAdminId());
                m.put("paymentAdminName", displayName(l.getPaymentAdminId(), adminNames));
                m.put("offlineAdminId", l.getOfflineAdminId());
                m.put("offlineTime", l.getOfflineTime());
                m.put("offlineAdminName", displayName(l.getOfflineAdminId(), adminNames));
                list.add(m);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("list", list);
            data.put("total", result.getTotal());
            return new PccAjaxResult(200, "获取标的列表成功", data);
        } catch (Exception e) {
            log.error("查询标的列表失败: {}", e.getMessage(), e);
            return new PccAjaxResult(500, "查询标的列表失败: " + getErrMsg(e), null);
        }
    }

    /**
     * 创建标的（后台测试用）
     * 自动生成标的编号、设置状态为"募资中"、计算起止日期
     */
    @ApiOperation("创建标的（后台测试用）")
    @PostMapping
    @RequireAdminRole(value = AdminRoles.SUPER_ADMIN,
            message = "创建标的属于高危动作，仅限超级管理员")
    public PccAjaxResult create(@RequestBody Map<String, Object> params) {
        try {
            String title = (String) params.get("title");
            String borrowerName = (String) params.get("borrowerName");
            Integer borrowerId = (Integer) params.get("borrowerId");
            BigDecimal amount = new BigDecimal(params.get("amount").toString());
            BigDecimal rate = new BigDecimal(params.get("rate").toString());
            Integer term = (Integer) params.get("term");
            String purpose = (String) params.get("purpose");
            Integer riskLevel = (Integer) params.getOrDefault("riskLevel", 1);

            if (title == null || title.trim().isEmpty()) {
                return new PccAjaxResult(400, "标的名称不能为空", null);
            }
            if (borrowerName == null || borrowerName.trim().isEmpty()) {
                return new PccAjaxResult(400, "借款人姓名不能为空", null);
            }
            if (borrowerId == null || borrowerId <= 0) {
                return new PccAjaxResult(400, "借款人ID无效", null);
            }
            if (amount == null || amount.compareTo(new BigDecimal("1000")) < 0) {
                return new PccAjaxResult(400, "标的金额不能少于1000元", null);
            }
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                return new PccAjaxResult(400, "年化利率必须大于0", null);
            }
            if (term == null || term < 1) {
                return new PccAjaxResult(400, "借款期限必须大于0天", null);
            }

            Lend lend = new Lend();
            lend.setLendNo(LendNoUtils.getLendNo());
            lend.setTitle(title.trim());
            lend.setUserId(borrowerId.longValue());
            lend.setAmount(amount);
            lend.setPeriod(term);
            lend.setLendYearRate(rate);
            lend.setReturnMethod(1);
            lend.setLowestAmount(new BigDecimal("100"));
            lend.setInvestAmount(BigDecimal.ZERO);
            lend.setInvestNum(0);
            lend.setStatus(LendStatusEnum.FUNDING.getCode());
            lend.setPublishDate(LocalDateTime.now());
            // 该接口直接置为"募资中"、绕过 publish()，等同于一次上架审核通过，
            // 因此审核留痕也要一并写入，否则这条标的在台账里会显示"无审核记录"
            lend.setCheckTime(LocalDateTime.now());
            lend.setCheckAdminId(currentAdminId());
            lend.setLendStartDate(LocalDate.now().plusDays(1));
            lend.setLendEndDate(LocalDate.now().plusDays(term));
            lend.setLendInfo(purpose != null ? purpose.trim() : "");
            lend.setCreateTime(LocalDateTime.now());
            lend.setUpdateTime(LocalDateTime.now());

            boolean ok = lendService.save(lend);

            if (ok) {
                // 该接口直接置为"募资中"、绕过 publish()，所以在这里补上借款人侧合同的预生成，
                // 保证合同台账与标的一致（否则放款时会因缺《放款确认书》被卡）
                try {
                    contractService.getOrCreate(com.example.demo.enums.ContractBizTypeEnum.LEND,
                            lend.getId(), com.example.demo.enums.ContractTypeEnum.LOAN, lend.getUserId());
                    contractService.getOrCreate(com.example.demo.enums.ContractBizTypeEnum.LEND,
                            lend.getId(), com.example.demo.enums.ContractTypeEnum.PAYMENT, lend.getUserId());
                } catch (Exception e) {
                    log.error("预生成标的合同失败: lendId={}, error={}", lend.getId(), e.getMessage(), e);
                }
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("id", lend.getId());
                resultData.put("message", "创建成功");
                log.info("标的创建成功: id={}, title={}, amount={}", lend.getId(), title, amount);
                return new PccAjaxResult(200, "标的创建成功", resultData);
            } else {
                return new PccAjaxResult(500, "标的创建失败", null);
            }
        } catch (NumberFormatException e) {
            log.error("参数格式错误: {}", e.getMessage());
            return new PccAjaxResult(400, "参数格式错误: " + e.getMessage(), null);
        } catch (Exception e) {
            log.error("创建标的失败: {}", e.getMessage(), e);
            return new PccAjaxResult(500, "创建标的失败: " + getErrMsg(e), null);
        }
    }

    /**
     * 下架标的
     * 将标的状态从任意状态改为"已下架"
     */
    @ApiOperation("下架标的")
    @PutMapping("/{id}/offline")
    @RequireAdminRole(value = AdminRoles.SUPER_ADMIN,
            message = "下架标的会触发投资人退资，仅限超级管理员")
    public PccAjaxResult offline(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return new PccAjaxResult(400, "标的ID无效", null);
        }
        try {
            boolean ok = lendService.offline(id, currentAdminId());
            if (ok) {
                log.info("标的下架成功: id={}", id);
                return new PccAjaxResult(200, "下架成功", ok);
            } else {
                return new PccAjaxResult(500, "下架失败，请检查标的状态", null);
            }
        } catch (Exception e) {
            log.error("下架标的失败: id={}, error={}", id, e.getMessage(), e);
            return new PccAjaxResult(500, "下架失败: " + getErrMsg(e), null);
        }
    }

    /**
     * 发布标的
     * 将标的从"待发布"状态改为"募资中"
     */
    @ApiOperation("发布标的（待发布→募资中）")
    @PutMapping("/{id}/publish")
    @RequireAdminRole(value = AdminRoles.SUPER_ADMIN,
            message = "发布标的将对投资人公开募资，仅限超级管理员")
    public PccAjaxResult publish(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return new PccAjaxResult(400, "标的ID无效", null);
        }
        try {
            boolean ok = lendService.publish(id, currentAdminId());
            if (ok) {
                log.info("标的发布成功: id={}", id);
                return new PccAjaxResult(200, "发布成功", ok);
            } else {
                return new PccAjaxResult(500, "发布失败，请检查标的是否处于待发布状态", null);
            }
        } catch (Exception e) {
            log.error("发布标的失败: id={}, error={}", id, e.getMessage(), e);
            return new PccAjaxResult(500, "发布失败: " + getErrMsg(e), null);
        }
    }

    /**
     * 获取标的的投资列表
     * 包含投资人信息、投资金额、预期收益等详细信息
     * 自动计算预期收益 = 金额 × 年化利率 × 天数 / 365
     */
    @ApiOperation("获取标的的投资列表")
    @GetMapping("/{id}/investments")
    public PccAjaxResult investments(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return new PccAjaxResult(400, "标的ID无效", null);
        }
        try {
            List<LendItem> items = lendItemService.listByLendId(id);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            List<Map<String, Object>> investmentList = items.stream().map(item -> {
                Map<String, Object> map = new HashMap<>();

                map.put("id", item.getId());
                map.put("projectId", item.getLendId());
                map.put("investorId", item.getInvestUserId());
                map.put("investorName", item.getInvestName());
                map.put("amount", item.getInvestAmount());

                int days = 30;
                if (item.getLendEndDate() != null && item.getLendStartDate() != null) {
                    days = (int) ChronoUnit.DAYS.between(item.getLendStartDate(), item.getLendEndDate());
                }

                BigDecimal expectAmount = BigDecimal.ZERO;
                if (item.getInvestAmount() != null && item.getLendYearRate() != null) {
                    expectAmount = item.getInvestAmount()
                            .multiply(item.getLendYearRate())
                            .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal(days))
                            .divide(new BigDecimal("365"), 2, RoundingMode.HALF_UP);
                }

                map.put("expectedReturn", expectAmount);
                map.put("term", days);
                map.put("status", item.getStatus());
                map.put("investTime", item.getInvestTime() != null ?
                        item.getInvestTime().format(formatter) : "");

                return map;
            }).collect(Collectors.toList());

            Map<String, Object> data = new HashMap<>();
            data.put("list", investmentList);
            data.put("total", investmentList.size());

            log.info("查询投资列表成功: projectId={}, count={}", id, investmentList.size());
            return new PccAjaxResult(200, "获取投资列表成功", data);
        } catch (Exception e) {
            log.error("查询投资列表失败: projectId={}, error={}", id, e.getMessage(), e);
            return new PccAjaxResult(500, "查询投资列表失败: " + getErrMsg(e), null);
        }
    }

    /**
     * 满标放款（服务端直连银行同步放款）。
     *
     * <p>旺旺银行 {@code /userInvest/AgreeAccountLendProject} 是<b>同步 JSON 接口，不发异步回调</b>。
     * 旧实现返回一张自动提交表单让浏览器跳银行，结果只拿到一段 JSON，
     * 平台的 {@code notifyMakeLoan} 永远不会被触发，标的就此永久卡在「已满标待放款」。
     * 改为服务端直连：银行返回 resultCode=0000 即视为划转成功，平台随即落账。
     */
    @ApiOperation("满标放款（服务端直连银行同步放款并落账）")
    @PutMapping("/{id}/makeLoan")
    @RequireAdminRole(value = AdminRoles.SUPER_ADMIN,
            message = "满标放款会真实划转资金，仅限超级管理员")
    public PccAjaxResult makeLoan(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return new PccAjaxResult(400, "标的ID无效", null);
        }
        try {
            Map<String, Object> result = lendReturnService.makeLoanSync(id, currentAdminId());
            boolean ok = Boolean.TRUE.equals(result.get("success"));
            String msg = String.valueOf(result.get("message"));
            log.info("满标放款完成: projectId={}, success={}, msg={}", id, ok, msg);
            return new PccAjaxResult(ok ? 200 : 500, msg, result);
        } catch (Exception e) {
            log.error("满标放款失败: projectId={}, error={}", id, e.getMessage(), e);
            return new PccAjaxResult(500, "放款失败: " + getErrMsg(e), null);
        }
    }

    /**
     * 查询标的还款计划
     * 返回该标的所有期数的还款计划详情
     */
    @ApiOperation("查询标的还款计划")
    @GetMapping("/{id}/returns")
    public PccAjaxResult returns(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return new PccAjaxResult(400, "标的ID无效", null);
        }
        try {
            List<LendReturn> list = lendReturnService.listByLendId(id);
            log.info("查询还款计划成功: projectId={}, count={}", id, list.size());
            return new PccAjaxResult(200, "获取还款计划成功", list);
        } catch (Exception e) {
            log.error("查询还款计划失败: projectId={}, error={}", id, e.getMessage(), e);
            return new PccAjaxResult(500, "查询还款计划失败: " + getErrMsg(e), null);
        }
    }

    /**
     * 手动触发逾期扫描
     * 测试/补单用，生产环境由定时任务每天02:00自动执行
     * 扫描所有应还未还的还款计划，标记为逾期状态
     */
    @ApiOperation("手动触发逾期扫描（测试/补单用，定时任务每天02:00自动执行）")
    @PostMapping("/overdueCheck")
    @RequireAdminRole(value = AdminRoles.SUPER_ADMIN,
            message = "逾期扫描会批量变更还款状态，仅限超级管理员")
    public PccAjaxResult overdueCheck() {
        try {
            int count = lendReturnService.overdueCheck();
            log.info("逾期扫描完成，处理记录数: {}", count);
            return new PccAjaxResult(200, "逾期扫描完成，处理记录数：" + count, count);
        } catch (Exception e) {
            log.error("逾期扫描失败: error={}", e.getMessage(), e);
            return new PccAjaxResult(500, "逾期扫描失败: " + getErrMsg(e), null);
        }
    }

    private String getErrMsg(Exception e) {
        if (e instanceof ResourceNotFoundException) {
            String msg = ((ResourceNotFoundException) e).getMsg();
            if (msg != null) return msg;
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** 当前登录管理员ID（仅用于审计留痕，取不到就留空，绝不影响业务） */
    private Long currentAdminId() {
        Integer adminId = LoginUserContext.getUserid();
        return adminId == null ? null : adminId.longValue();
    }

    /** 批量查管理员姓名；name 为空时回退 username */
    private Map<Long, String> resolveAdminNames(Set<Long> adminIds) {
        if (adminIds == null || adminIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return sysAdminMapper.selectBatchIds(adminIds).stream()
                    .collect(Collectors.toMap(
                            SysAdmin::getId,
                            a -> StringUtils.hasText(a.getName()) ? a.getName() : a.getUsername(),
                            (a, b) -> a));
        } catch (Exception e) {
            log.warn("反查管理员姓名失败，降级为只显示ID: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String displayName(Long adminId, Map<Long, String> adminNames) {
        if (adminId == null) {
            return null;
        }
        String name = adminNames.get(adminId);
        return StringUtils.hasText(name) ? name : ("管理员#" + adminId);
    }
}