package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.client.SMSApiSmsClient;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.UserBind;
import com.example.demo.entity.UserLoginRecord;
import com.example.demo.entity.dto.AdminUserQuery;
import com.example.demo.entity.dto.UserDto;
import com.example.demo.exception.BusinessException;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.example.demo.mapper.UserAccountMapper;
import com.example.demo.mapper.UserBindMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.MessageService;
import com.example.demo.service.UserLoginRecordService;
import com.example.demo.service.UserService;
import com.example.demo.service.IntegralGradeService;
import com.wwfinance.common.utils.TokenUtil;
import com.wwfinance.common.utils.MD5;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper,User>  implements UserService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SMSApiSmsClient smsApiSmsClient;

    @Autowired
    private UserLoginRecordService userLoginRecordService;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private UserBindMapper userBindMapper;

    @Autowired
    @Lazy
    private MessageService messageService;

    @Autowired
    private IntegralGradeService integralGradeService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final long TOKEN_TTL = 604800;

    @Override
    public User getUserById(Long id) {
        return baseMapper.selectById(id);
    }

    @Override
    public User getByMobile(String mobile) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getMobile, mobile);
        queryWrapper.last("LIMIT 1");
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

    @Override
    public void addMember(String mobile, String nickName, String name, String idCard, Integer integral) {
        // 1. 参数校验
        if (StringUtils.isBlank(mobile)) {
            throw new BusinessException(400, "手机号不能为空");
        }
        // 2. 手机号不能重复注册
        User exist = getByMobile(mobile);
        if (exist != null) {
            throw new BusinessException(400, "该手机号已注册");
        }
        // 3. 组装用户：默认密码 123456（MD5），普通用户，正常状态
        User user = new User();
        user.setMobile(mobile);
        user.setNickName(StringUtils.isBlank(nickName) ? mobile : nickName);
        user.setName(name);
        user.setIdCard(idCard);
        user.setIntegral(integral == null ? 0 : integral);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setUserType(0);
        user.setStatus(1);
        user.setBindStatus(0);
        user.setBorrowAuthStatus(0);
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        try {
            save(user);
        } catch (DuplicateKeyException e) {
            // 同上：唯一索引 uk_mobile 是并发下的最终防线
            throw new BusinessException(400, "该手机号已注册");
        }
    }

    @Override
    public String login(String mobile, String password) {
        User user = getByMobile(mobile);
        if (user == null) {
            saveLoginRecord(null, mobile, 0, 0);
            throw new ResourceNotFoundException(404, "用户不存在");
        }
        if (!matchesPassword(password, user.getPassword())) {
            saveLoginRecord(user.getId(), mobile, 0, 0);
            throw new BusinessException(400, "密码错误");
        }
        if (isMd5Password(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(password));
            updateById(user);
        }
        String token = TokenUtil.generateMerchantToken(user.getId().toString());
        // Redis key 按角色命名空间隔离，避免用户与管理员主键相同导致会话互相顶掉
        stringRedisTemplate.opsForValue().set("login:token:user:" + user.getId(), token, TOKEN_TTL, TimeUnit.SECONDS);
        saveLoginRecord(user.getId(), mobile, 1, 1);
        return token;
    }

    @Override
    public String loginByCode(String mobile, String code) {
        User user = getByMobile(mobile);
        if (user == null) {
            saveLoginRecord(null, mobile, 0, 0);
            throw new ResourceNotFoundException(404, "用户不存在");
        }
        if (!checkSmsCode(mobile, code)) {
            saveLoginRecord(user.getId(), mobile, 0, 0);
            throw new BusinessException(400, "验证码错误或已过期");
        }
        String token = TokenUtil.generateMerchantToken(user.getId().toString());
        stringRedisTemplate.opsForValue().set("login:token:user:" + user.getId(), token, TOKEN_TTL, TimeUnit.SECONDS);
        saveLoginRecord(user.getId(), mobile, 1, 1);
        return token;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserDto userDTO) {
        if (!checkSmsCode(userDTO.getMobile(), userDTO.getCode())) {
            throw new BusinessException(400, "验证码错误或已过期");
        }
        User exist = getByMobile(userDTO.getMobile());
        if (exist != null) {
            throw new BusinessException(400, "手机号已经存在");
        }
        if (!userDTO.getPassword().equals(userDTO.getPasswordto())) {
            throw new BusinessException(400, "两次输入的密码不正确");
        }
        User user = new User();
        user.setMobile(userDTO.getMobile());
        user.setUserType(userDTO.getUserType() != null ? userDTO.getUserType() : 1);
        user.setName(userDTO.getMobile());
        user.setNickName(userDTO.getMobile());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setStatus(1);
        user.setBorrowAuthStatus(0);
        user.setBindStatus(0);
        try {
            save(user);
        } catch (DuplicateKeyException e) {
            // 并发兜底：上面的 getByMobile 查重是 check-then-insert，两个并发请求可能同时通过；
            // 这里靠唯一索引 uk_mobile 兜底，把数据库层的冲突翻译成业务文案。
            throw new BusinessException(400, "手机号已经存在");
        }
        UserAccount userAccount = new UserAccount();
        userAccount.setUserId(user.getId());
        userAccountMapper.insert(userAccount);
        messageService.sendRegisterNotice(user.getId(), user.getMobile());
    }

    @Override
    public UserDto getCurrentUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "用户不存在");
        }
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setMobile(user.getMobile());
        userDto.setUserType(user.getUserType());
        userDto.setName(user.getName());
        userDto.setNickName(user.getNickName());
        userDto.setIdCard(user.getIdCard());
        userDto.setEmail(user.getEmail());
        userDto.setHeadImg(user.getHeadImg());
        userDto.setBorrowAuthStatus(user.getBorrowAuthStatus());
        userDto.setInvestAuthStatus(user.getInvestAuthStatus());
        userDto.setIntegral(user.getIntegral());
        userDto.setBindStatus(user.getBindStatus());
        userDto.setBindCode(user.getBindCode());
        LambdaQueryWrapper<UserBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBind::getUserId, userId);
        wrapper.last("LIMIT 1");
        UserBind userBind = userBindMapper.selectOne(wrapper);
        userDto.setBindStatus(userBind != null ? userBind.getStatus() : 0);
        return userDto;
    }

    @Override
    public void updateProfile(Long userId, UserDto userDTO) {
        User user = getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "用户不存在");
        }
        user.setName(userDTO.getName());
        user.setNickName(userDTO.getNickName());
        user.setEmail(userDTO.getEmail());
        user.setHeadImg(userDTO.getHeadImg());
        updateById(user);
    }

    @Override
    public void updateStatus(Long userId, Integer status) {
        User user = getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "会员不存在");
        }
        user.setStatus(status);
        updateById(user);
    }

    @Override
    public boolean checkMobileAvailable(String mobile) {
        return getByMobile(mobile) == null;
    }

    @Override
    public java.util.Map<String, Object> getBorrowAmount(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "用户不存在");
        }
        java.math.BigDecimal amount = integralGradeService.getBorrowAmountByIntegral(user.getIntegral());
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("integral", user.getIntegral() == null ? 0 : user.getIntegral());
        data.put("borrowAmount", amount);
        return data;
    }

    /**
     * 记录登录日志（登录结果与登录方式落库，供后台登录日志页筛选）
     */
    private void saveLoginRecord(Long userId, String username, int loginResult, int loginType) {
        try {
            UserLoginRecord record = new UserLoginRecord();
            record.setUserId(userId);
            record.setIp("127.0.0.1");
            record.setLoginResult(loginResult);
            record.setLoginType(loginType);
            userLoginRecordService.save(record);
        } catch (Exception e) {
            log.warn("登录日志记录失败: " + e.getMessage());
        }
    }

    /**
     * 校验短信验证码（Feign 调用容错：短信服务不可用时返回 false 并给出友好提示）
     */
    private boolean checkSmsCode(String mobile, String code) {
        try {
            return Boolean.TRUE.equals(smsApiSmsClient.checkCode(mobile, code).get("data"));
        } catch (Exception e) {
            log.error("短信验证码服务调用失败，mobile=" + mobile + ", error=" + e.getMessage());
            throw new BusinessException(500, "短信服务不可用，请稍后再试");
        }
    }

    private boolean isMd5Password(String storedPassword) {
        return storedPassword != null && storedPassword.length() == 32 && storedPassword.matches("[0-9a-f]{32}");
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        // 仅允许 BCrypt 或历史 MD5 兼容两种方式，不接受明文直比（安全后门）
        if (isMd5Password(storedPassword)) {
            return MD5.encrypt(rawPassword).equals(storedPassword);
        }
        return passwordEncoder.matches(rawPassword, storedPassword);
    }

}