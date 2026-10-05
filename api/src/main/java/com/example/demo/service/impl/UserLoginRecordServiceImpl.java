package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.User;
import com.example.demo.entity.UserLoginRecord;
import com.example.demo.mapper.UserLoginRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.UserLoginRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import com.example.demo.utils.PageUtils;

@Slf4j
@Service
public class UserLoginRecordServiceImpl extends ServiceImpl<UserLoginRecordMapper, UserLoginRecord>
        implements UserLoginRecordService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public Page<UserLoginRecord> adminPageList(Integer pageNum, Integer pageSize, String keyword, Integer loginResult) {
        Page<UserLoginRecord> pageParam = PageUtils.of(pageNum, pageSize);
        LambdaQueryWrapper<UserLoginRecord> wrapper = new LambdaQueryWrapper<>();
        // keyword：按手机号在 user 表匹配出 userId 列表（之前误用来 like ip，搜索永远无效）
        if (keyword != null && !keyword.isEmpty()) {
            List<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .like(User::getMobile, keyword))
                    .stream().map(User::getId).collect(Collectors.toList());
            if (userIds.isEmpty()) {
                // 没有匹配用户 → 返回空结果
                wrapper.eq(UserLoginRecord::getId, -1L);
            } else {
                wrapper.in(UserLoginRecord::getUserId, userIds);
            }
        }
        // 登录结果筛选（此前参数被完全忽略）
        wrapper.eq(loginResult != null, UserLoginRecord::getLoginResult, loginResult);
        wrapper.orderByDesc(UserLoginRecord::getCreateTime);
        return this.page(pageParam, wrapper);
    }
}