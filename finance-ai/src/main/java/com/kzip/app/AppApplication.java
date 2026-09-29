package com.kzip.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.kzip.app.mapper")
public class AppApplication {

    static {
        // Windows 默认 file.encoding=GBK，导致 ChromaApi 内部 String.getBytes() 编码中文为乱码
        // 用反射强制修改 Charset.defaultCharset() 为 UTF-8
        try {
            Field field = Charset.class.getDeclaredField("defaultCharset");
            field.setAccessible(true);
            field.set(null, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("无法设置默认编码为UTF-8: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
    }
}