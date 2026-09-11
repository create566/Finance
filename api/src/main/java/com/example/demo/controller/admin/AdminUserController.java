package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.User;
import com.example.demo.entity.dto.AdminUserQuery;
import com.example.demo.service.UserService;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台会员管理（对齐前端 /admin/core/member/** 约定）
 */
@Api(tags = "后台会员管理")
@RestController
@RequestMapping("/admin/core/member")
@Slf4j
public class AdminUserController {

    @Autowired
    private UserService userService;

    @ApiOperation("获取会员分页列表")
    @GetMapping("/page")
    public PccAjaxResult page(
            @ApiParam(value = "当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam(value = "每页记录数") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam(value = "手机号/姓名模糊搜索") @RequestParam(required = false) String keyword,
            @ApiParam(value = "状态: 1正常 0禁用") @RequestParam(required = false) Integer status) {
        Page<User> pageParam = new Page<>(pageNum, pageSize);
        AdminUserQuery query = new AdminUserQuery();
        query.setMobile(keyword);
        query.setStatus(status);
        IPage<User> pageModel = userService.listPage(pageParam, query);
        Map<String, Object> data = new HashMap<>();
        data.put("list", pageModel.getRecords());
        data.put("total", pageModel.getTotal());
        return new PccAjaxResult(200, "获取会员分页列表成功", data);
    }

    @ApiOperation("修改会员状态（启用/禁用）")
    @PutMapping("/{id}/status")
    public PccAjaxResult updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Integer newStatus = body.get("status") == null ? null : Integer.valueOf(body.get("status").toString());
        if (newStatus == null) {
            return new PccAjaxResult(500, "status不能为空");
        }
        User user = userService.getById(id);
        if (user == null) {
            return new PccAjaxResult(500, "会员不存在");
        }
        user.setStatus(newStatus);
        userService.updateById(user);
        return new PccAjaxResult(200, "修改成功");
    }

    @ApiOperation("新增会员（管理员手动开户）")
    @PostMapping
    public PccAjaxResult addMember(@RequestBody Map<String, Object> body) {
        String mobile = body.get("phone") == null ? null : body.get("phone").toString();
        String nickName = body.get("nickname") == null ? null : body.get("nickname").toString();
        String name = body.get("realName") == null ? null : body.get("realName").toString();
        String idCard = body.get("idCard") == null ? null : body.get("idCard").toString();
        Integer integral = body.get("score") == null ? 0 : Integer.valueOf(body.get("score").toString());
        userService.addMember(mobile, nickName, name, idCard, integral);
        return new PccAjaxResult(200, "新增成功");
    }
}
