package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.BorrowInfo;
import com.example.demo.entity.SysAdmin;
import com.example.demo.mapper.SysAdminMapper;
import com.example.demo.service.BorrowInfoService;
import com.wwfinance.common.utils.LoginUserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

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
 * 后台借款记录管理（对齐前端 /admin/core/borrow/record/** 约定）
 * <p>
 * 审核动作会写入 check_admin_id / check_time 留痕，列表接口把管理员姓名一并返回。
 */
@Api(tags = "后台借款记录管理")
@RestController
@RequestMapping("/admin/core/borrow/record")
@Slf4j
public class AdminBorrowInfoController {

    @Autowired
    private BorrowInfoService borrowInfoService;

    @Autowired
    private SysAdminMapper sysAdminMapper;

    /**
     * 用 Spring 容器里的 ObjectMapper（而非 new ObjectMapper()），
     * 好处是它带 JavaTimeModule，LocalDateTime 的序列化格式与控制器直接返回实体时完全一致，
     * 前端既有的时间展示不会因为"改成手工转 Map"而变形。
     */
    @Autowired
    private ObjectMapper objectMapper;

    @ApiOperation("获取借款记录分页列表")
    @GetMapping("/page")
    public PccAjaxResult page(
            @ApiParam(value = "当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam(value = "每页记录数") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam(value = "用户ID精确 / 借款金额模糊") @RequestParam(required = false) String keyword,
            @ApiParam(value = "状态: 0草稿 1审核中 2通过 -1拒绝") @RequestParam(required = false) Integer status) {
        Page<BorrowInfo> result = borrowInfoService.adminPageList(pageNum, pageSize, keyword, status);
        List<BorrowInfo> records = result.getRecords();

        // 批量反查审核管理员姓名（一次查询，避免 N+1）
        Set<Long> adminIds = new HashSet<>();
        for (BorrowInfo b : records) {
            if (b.getCheckAdminId() != null) {
                adminIds.add(b.getCheckAdminId());
            }
        }
        Map<Long, String> adminNames = resolveAdminNames(adminIds);

        // check_admin_id / check_time 带 @JsonIgnore（不对前台暴露），
        // 这里转 Map 后显式回填，保持响应结构与前端既有字段路径完全一致（扁平，不嵌套）
        List<Map<String, Object>> list = new ArrayList<>(records.size());
        for (BorrowInfo b : records) {
            Map<String, Object> m = objectMapper.convertValue(
                    b, new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            m.put("checkAdminId", b.getCheckAdminId());
            m.put("checkTime", b.getCheckTime());
            m.put("checkAdminName", displayName(b.getCheckAdminId(), adminNames));
            list.add(m);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "获取借款记录列表成功", data);
    }

    @ApiOperation("审核借款申请（通过/拒绝）")
    @PutMapping("/{id}/audit")
    public PccAjaxResult audit(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Integer status = (Integer) body.get("status");
        if (status == null) {
            return new PccAjaxResult(500, "status不能为空");
        }
        Integer adminIdInt = LoginUserContext.getUserid();
        Long adminId = adminIdInt == null ? null : adminIdInt.longValue();
        boolean ok = borrowInfoService.auditBorrowInfo(id, status, adminId);
        boolean approved = Integer.valueOf(2).equals(status);
        return new PccAjaxResult(200, approved ? "审批通过" : "已拒绝", ok);
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
