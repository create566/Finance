package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.User;
import com.example.demo.entity.dto.AdminUserQuery;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper,User>  implements UserService {

    @Override
    public User getUserById(Long id) {
        return baseMapper.selectById(id);
    }

    @Override
    public User getByMobile(String mobile) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getMobile, mobile);
        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    public void updateBindStatus(Long userId, Integer status, String bindCode) {
        User user = baseMapper.selectById(userId);
        if (user != null) {
            user.setBindStatus(status);
            user.setBindCode(bindCode);
            baseMapper.updateById(user);
        }
    }

    @Override
    public IPage<User> listPage(Page<User> pageParam, AdminUserQuery adminUserQuery){
        if(adminUserQuery == null){
            return baseMapper.selectPage(pageParam, null);
        }
        LambdaQueryWrapper<User> userQueryWrapper = new LambdaQueryWrapper<>();
        userQueryWrapper.eq(StringUtils.isNotBlank(adminUserQuery.getMobile()), User::getMobile, adminUserQuery.getMobile())
                .eq(adminUserQuery.getStatus() != null, User::getStatus, adminUserQuery.getStatus())
                .eq(adminUserQuery.getUserType() != null, User::getUserType, adminUserQuery.getUserType());
        return baseMapper.selectPage(pageParam, userQueryWrapper);
    }

    @Override
    public List<User> selectByCondition(String name, String mobile) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.isNotBlank(name), User::getName, name)
                .eq(StringUtils.isNotBlank(mobile), User::getMobile, mobile)
                .orderByDesc(User::getId);
        return baseMapper.selectList(queryWrapper);
    }

}