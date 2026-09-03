package com.kzip.app.controller;

import com.kzip.app.model.User;
import com.kzip.app.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.PropertySource;

@PropertySource(value = "classpath:application.properties", encoding = "UTF-8")
@RestController
@Validated
public class HelloController {


    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("Hello, Spring Boot!");
    }

    @GetMapping("/hellobean/{id}")
    public Result<String> hellobean(@PathVariable @Min(1) Long id) {
        print();
        System.out.println(name);
        return Result.success(myMessage + name + id.toString());
    }

    @GetMapping("/user/{id}")
    public Result<User> getUser(@PathVariable @Min(1) Long id) {
        // 模拟查到的用户
        User user = userService.getById(id);
        // 统一用 Result 包裹返回
        return Result.success(user);
    }

    @Autowired
    private String myMessage;
    public void print() { System.out.println(myMessage); }


    @Resource
    private UserService userService;



    @Value("${my.name}")
    private String name;
}
