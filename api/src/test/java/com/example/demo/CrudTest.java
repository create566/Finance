package com.example.demo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 MyBatis-Plus 增删改查 + 逻辑删除（@TableLogic）
 * 会在本地库写入一条测试数据，测试完成后该数据被逻辑删除（is_deleted=1）
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrudTest {

    @Autowired
    private UserService userService;

    // 用时间戳生成唯一手机号，避免重复注册冲突
    private static final String TEST_MOBILE = "199" + System.currentTimeMillis() % 100000000L;
    private static Long testUserId;

    @Test
    @Order(1)
    void insert() {
        User user = new User();
        user.setMobile(TEST_MOBILE);
        user.setName("CRUD测试用户");
        user.setPassword("123456");
        user.setStatus(1);
        boolean saved = userService.save(user);
        assertTrue(saved, "新增失败");
        assertNotNull(user.getId(), "新增后应回填自增主键");
        testUserId = user.getId();
        System.out.println("【增】成功, id=" + testUserId + ", mobile=" + TEST_MOBILE);
    }

    @Test
    @Order(2)
    void select() {
        User user = userService.getById(testUserId);
        assertNotNull(user, "【查】失败: 按 id 查不到刚插入的数据");
        assertEquals(TEST_MOBILE, user.getMobile(), "【查】失败: 手机号不一致");
        assertEquals("CRUD测试用户", user.getName(), "【查】失败: name 不一致");
        System.out.println("【查】成功: " + user.getName() + " / " + user.getMobile());
    }

    @Test
    @Order(3)
    void update() {
        User user = new User();
        user.setId(testUserId);
        user.setName("CRUD测试用户-已修改");
        boolean updated = userService.updateById(user);
        assertTrue(updated, "【改】失败");
        User dbUser = userService.getById(testUserId);
        assertEquals("CRUD测试用户-已修改", dbUser.getName(), "【改】失败: 修改未落库");
        System.out.println("【改】成功: " + dbUser.getName());
    }

    @Test
    @Order(4)
    void logicDelete() {
        boolean removed = userService.removeById(testUserId);
        assertTrue(removed, "【删】失败");

        // @TableLogic 生效验证: 逻辑删除后业务查询查不到
        User user = userService.getById(testUserId);
        assertNull(user, "【删】失败: 逻辑删除后仍能查到, @TableLogic 可能未生效");

        long count = userService.count(new LambdaQueryWrapper<User>().eq(User::getId, testUserId));
        assertEquals(0, count, "【删】失败: count 应为 0");

        System.out.println("【删】成功: id=" + testUserId + " 已逻辑删除(is_deleted=1), 业务查询不可见");
    }
}
