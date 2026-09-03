package com.example.demo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
@SpringBootTest
class DbConnectTest {
    @Autowired
    private DataSource dataSource;
    @Autowired
    private UserService userService;
    /**
     * 1. 查看当前连的是哪个数据库
     */
    @Test
    void showDataSourceInfo() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            System.out.println("== 数据库连接信息 ==");
            System.out.println("URL: " + conn.getMetaData().getURL());
            System.out.println("当前库: " + conn.getCatalog());
            System.out.println("用户名: " + conn.getMetaData().getUserName());
        }
    }
    /**
     * 2. 验证 MyBatis-Plus 读通道是否通
     */
    @Test
    void crudCheck() {
        System.out.println("== 通过 MyBatis-Plus 查数据 ==");
        long count = userService.count();
        System.out.println("user 表总记录数: " + count);
        List<User> list = userService.list(new LambdaQueryWrapper<User>().last("limit 5"));
        list.forEach(u -> System.out.println("id=" + u.getId() + ", mobile=" + u.getMobile() + ", name=" + u.getName()));
    }
}