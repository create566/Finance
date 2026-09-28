package com.wwfinance.common.exception;

import com.wwfinance.common.result.PccAjaxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public PccAjaxResult handleException(Exception e) {
        // 安全修复：不把异常原文（SQL、文件路径、内部结构）返回给客户端，只记录日志
        log.error("系统异常", e);
        return new PccAjaxResult(500, "系统繁忙，请稍后再试");
    }

    @ExceptionHandler(value = BadSqlGrammarException.class)
    public PccAjaxResult handleException(BadSqlGrammarException e){
        log.error(e.getMessage(), e);
        return new PccAjaxResult(101, "sql语法错误");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public PccAjaxResult handleResourceNotFoundException(ResourceNotFoundException e) {
        log.error(e.getMessage(), e);
        return new PccAjaxResult(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public PccAjaxResult handleValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验失败: {}", msg);
        return new PccAjaxResult(400, msg);
    }

    @ExceptionHandler(BindException.class)
    public PccAjaxResult handleBind(BindException e) {
        FieldError fieldError = e.getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数绑定失败";
        log.warn("参数绑定失败: {}", msg);
        return new PccAjaxResult(400, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public PccAjaxResult handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("约束校验失败: {}", msg);
        return new PccAjaxResult(400, msg);
    }
}