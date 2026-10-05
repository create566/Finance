package com.kzip.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.charset.Charset;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.kzip.app.mapper")
public class AppApplication {

    static {
        // Windows 默认 file.encoding=GBK，导致 ChromaApi 内部 String.getBytes() 按默认编码
        // 把中文写成 GBK 而乱码。在 JVM 首次访问默认字符集前，把编码固定为 UTF-8：
        // 先设置 file.encoding / sun.jnu.encoding 系统属性，再触发一次默认字符集懒加载，
        // 让 Charset.defaultCharset() 读到 UTF-8。避免用反射改私有字段
        // （反射对 JDK 版本敏感、易被模块系统/安全策略拦截，属于脆弱方案）。
        // 说明：maven 启动时 spring-boot-maven-plugin 的 jvmArguments 已带 -Dfile.encoding=UTF-8，
        // 此处作为 IDE 直跑等未显式传参场景的兜底。
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.jnu.encoding", "UTF-8");
        Charset.defaultCharset();
    }

    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
    }
}