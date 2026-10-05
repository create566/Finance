package com.example.demo.common;

import com.example.demo.exception.BusinessException;
import com.example.demo.exception.UnauthorizedException;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.wwfinance.common.result.PccAjaxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public PccAjaxResult handleBusinessException(BusinessException e) {
        log.warn("业务异常: [{}] {}", e.getCode(), e.getMessage());
        return new PccAjaxResult(e.getCode(), e.getMessage(), null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public PccAjaxResult handleResourceNotFound(ResourceNotFoundException e) {
        // 透传业务侧传入的 code（400/403/404/500），不再一律 404
        int code = e.getCode() != null ? e.getCode() : 500;
        log.warn("业务异常: [{}] {}", code, e.getMessage());
        return new PccAjaxResult(code, e.getMessage(), null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public PccAjaxResult handleUnauthorized(UnauthorizedException e) {
        log.warn("未授权访问: {}", e.getMessage());
        return new PccAjaxResult(401, e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public PccAjaxResult handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return new PccAjaxResult(400, "参数校验失败: " + message, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public PccAjaxResult handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return new PccAjaxResult(400, "请求数据格式错误，请检查JSON格式", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public PccAjaxResult handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("文件大小超限: {}", e.getMessage());
        return new PccAjaxResult(400, "文件大小超过限制", null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public PccAjaxResult handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return new PccAjaxResult(400, e.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public PccAjaxResult handleException(Exception e) {
        log.error("系统内部错误", e);
        return new PccAjaxResult(500, "系统内部错误，请联系管理员", null);
    }
}