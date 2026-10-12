package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.User;
import com.example.demo.entity.dto.AdminUserQuery;
import com.example.demo.entity.dto.UserDto;

public interface UserService  extends IService<User> {

    IPage<User> listPage(Page<User> pageParam, AdminUserQuery adminUserQuery);

    User getUserById(Long id);

    User getByMobile(String mobile);

    void updateBindStatus(Long userId, Integer status, String bindCode);

    /**
     * 后台新增会员（管理员手动开户）
     */
    void addMember(String mobile, String nickName, String name, String idCard, Integer integral);

    /**
     * 密码登录：校验用户存在 + 密码匹配 + 记录登录日志，返回 token
     */
    String login(String mobile, String password);

    /**
     * 验证码登录：校验用户存在 + 验证码匹配 + 记录登录日志，返回 token
     */
    String loginByCode(String mobile, String code);

    /**
     * 注册：验证码校验 + 手机号查重 + 密码一致性校验 + 落库 + 开账户 + 发送通知
     */
    void register(UserDto userDTO);

    /**
     * 获取当前登录用户信息（含绑卡状态）
     */
    UserDto getCurrentUserInfo(Long userId);

    /**
     * 更新当前登录用户资料
     */
    void updateProfile(Long userId, UserDto userDTO);

    /**
     * 修改会员状态（启用/禁用）
     */
    void updateStatus(Long userId, Integer status);

    /**
     * 检查手机号是否可注册（未被注册返回 true）
     */
    boolean checkMobileAvailable(String mobile);

    /**
     * 获取当前用户可借额度信息（积分 + 可借金额）
     */
    java.util.Map<String, Object> getBorrowAmount(Long userId);

}
