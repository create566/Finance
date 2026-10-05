package com.example.demo.controller.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.Message;
import com.example.demo.service.MessageService;
import com.example.demo.utils.LoginUserContext;
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
 * 前端用户站内消息接口
 */
@Api(tags = "站内消息")
@RestController
@RequestMapping("/api/core/message")
@Slf4j
public class UserMessageController {

    @Autowired
    private MessageService messageService;

    @ApiOperation("分页查询我的消息")
    @GetMapping("/myList")
    public PccAjaxResult myList(
            @ApiParam("当前页码") @RequestParam(required = false) Integer pageNum,
            @ApiParam("每页记录数") @RequestParam(required = false) Integer pageSize,
            @ApiParam("兼容参数：当前页码") @RequestParam(required = false) Integer page,
            @ApiParam("兼容参数：每页记录数") @RequestParam(required = false) Integer limit) {
        Integer userIdInt = LoginUserContext.getUserid();
        if (userIdInt == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        // 前端历史调用传的是 page/limit，两套参数都收 ——
        // 只认一套会让分页静默失效（永远返回第 1 页），很难被发现
        int pn = pageNum != null ? pageNum : (page != null ? page : 1);
        int ps = pageSize != null ? pageSize : (limit != null ? limit : 10);
        Page<Message> result = messageService.pageByUser(userIdInt.longValue(), pn, ps);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "获取消息列表成功", data);
    }

    @ApiOperation("未读消息数")
    @GetMapping("/unreadCount")
    public PccAjaxResult unreadCount() {
        Integer userIdInt = LoginUserContext.getUserid();
        if (userIdInt == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        long count = messageService.countUnread(userIdInt.longValue());
        return new PccAjaxResult(200, "获取未读数成功", count);
    }

    @ApiOperation("标记消息已读")
    @PutMapping("/read/{id}")
    public PccAjaxResult markRead(@PathVariable Long id) {
        Integer userIdInt = LoginUserContext.getUserid();
        if (userIdInt == null) {
            return new PccAjaxResult(401, "请先登录");
        }
        messageService.markRead(id, userIdInt.longValue());
        return new PccAjaxResult(200, "已读成功", true);
    }
}
