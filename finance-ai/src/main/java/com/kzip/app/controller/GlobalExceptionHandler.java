package com.kzip.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 全局异常处理器。
 * <p>
 * 作用：Controller 抛出的任何异常（包括 Spring MVC 在进入 Controller 之前抛出的）
 *       都会被这里对应的 @ExceptionHandler 拦截并返回统一的 Result 格式，
 *       控制台不会再出现 Servlet.service() for servlet [dispatcherServlet] threw exception 这种 ERROR 级堆栈。
 * <p>
 * 常见几类"进不到 Controller 方法内部就已经炸了"的异常：
 *   MultipartException                        → 上传接口没带 file / 不是 multipart 请求
 *   MissingServletRequestParameterException   → 缺少必传 query 参数
 *   MissingServletRequestPartException        → multipart 请求里缺了指定 part
 *   HttpRequestMethodNotSupportedException    → GET/POST 方法不匹配
 *   HttpMessageNotReadableException           → JSON body 格式错误（字段缺失、语法不对）
 *   HttpMediaTypeNotSupportedException        → Content-Type 不支持
 *   MethodArgumentNotValidException           → @Valid 参数校验失败
 *   Exception / RuntimeException              → 兜底，任何未显式处理的异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ================================================================
    //  上传相关：不打印 ERROR，只记录 warn，避免控制台被大量堆栈刷屏
    // ================================================================

    /**
     * Current request is not a multipart request
     *   → 前端调上传接口时没传 file、或者 Content-Type 不是 multipart/form-data
     */
    @ExceptionHandler(MultipartException.class)
    public Result<String> handleMultipartException(MultipartException e, HttpServletRequest request) {
        log.warn("上传请求格式错误 [{} {}]：{}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.fail("当前请求不是文件上传请求，请以 multipart/form-data 方式提交 file 参数");
    }

    /**
     * Required request part 'file' is not present
     *   → multipart 请求里缺了名为 file 的 part
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<String> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("上传请求缺少 part：{}", e.getRequestPartName());
        return Result.fail("请求参数不正确，缺少文件字段：" + e.getRequestPartName());
    }

    // ================================================================
    //  参数类：缺参数 / 参数校验失败
    // ================================================================

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<String> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必传参数：{}", e.getParameterName());
        return Result.fail("缺少必传参数：" + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().isEmpty()
                ? "参数校验失败"
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        log.warn("参数校验失败：{}", msg);
        return Result.fail(msg);
    }

    // ================================================================
    //  HTTP 方法/媒体类型
    // ================================================================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<String> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("HTTP 方法不支持：{}", e.getMethod());
        return Result.fail("不支持的请求方法：" + e.getMethod());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Result<String> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("Content-Type 不支持：{}", e.getContentType());
        return Result.fail("不支持的 Content-Type：" + e.getContentType());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<String> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体不可读：{}", e.getMessage());
        return Result.fail("请求体格式不正确，请检查 JSON 语法");
    }

    // ================================================================
    //  兜底：所有未显式处理的异常。这里需要打印完整堆栈，方便排查业务 bug
    // ================================================================

    @ExceptionHandler(Exception.class)
    public Result<String> handleOther(Exception e, HttpServletRequest request) {
        log.error("系统异常 [{} {}]", request.getMethod(), request.getRequestURI(), e);
        return Result.error("系统内部异常：" + e.getMessage());
    }
}
