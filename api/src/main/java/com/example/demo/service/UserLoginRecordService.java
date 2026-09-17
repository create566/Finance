package com.example.demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.UserLoginRecord;

public interface UserLoginRecordService extends IService<UserLoginRecord> {

    /**
     * 后台登录日志分页查询（按关键字模糊搜索用户名，按登录结果筛选）
     */
    Page<UserLoginRecord> adminPageList(Integer pageNum, Integer pageSize, String keyword, Integer loginResult);

}
