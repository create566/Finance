package com.kzip.app.service;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect        // 1. 标记这是一个切面（督察组）
@Component     // 2. 交给 Spring 管理
public class LogAspect {

    // 3. 定义切点：匹配 com.example.service 包下所有类的所有方法
    @Pointcut("execution(* com.kzip.app.Impl.*.*(..))")
    public void serviceMethods() {}

    // 4. 定义通知：在执行目标方法之前，打印日志
    @Before("serviceMethods()")
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("【AOP 拦截】方法即将被执行，记录日志...");
        // 获取方法签名
        String methodName = joinPoint.getSignature().getName();
        // 获取类名
        String className = joinPoint.getTarget().getClass().getSimpleName();
        // 或者获取全限定名
        String fullName = joinPoint.getSignature().getDeclaringTypeName() + "." + methodName;
        System.out.println("【AOP 拦截】" + className + "." + methodName + " 方法即将被执行，记录日志...");
    }
}