package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.UserLoginRecord;
import com.example.demo.mapper.UserLoginRecordMapper;
import com.example.demo.service.UserLoginRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserLoginRecordServiceImpl extends ServiceImpl<UserLoginRecordMapper, UserLoginRecord>
        implements UserLoginRecordService {

    @Override
    public Page<UserLoginRecord> adminPageList(Integer pageNum, Integer pageSize, String keyword, Integer loginResult) {
        Page<UserLoginRecord> pageParam = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<UserLoginRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(keyword != null && !keyword.isEmpty(), UserLoginRecord::getUsername, keyword);
        wrapper.eq(loginResult != null, UserLoginRecord::getLoginResult, loginResult);
        wrapper.orderByDesc(UserLoginRecord::getLoginTime);
        return this.page(pageParam, wrapper);
    }
}
