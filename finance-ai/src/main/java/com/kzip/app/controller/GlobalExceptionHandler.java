package com.kzip.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

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
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
    //  数值转换异常
    // ================================================================

    @ExceptionHandler(NumberFormatException.class)
    public Result<String> handleNumberFormat(NumberFormatException e) {
        log.warn("参数格式错误：{}", e.getMessage());
        return Result.fail("参数格式错误，期望数字类型：" + e.getMessage());
    }

    // ================================================================
    //  业务参数异常：由服务层抛 IllegalArgumentException 表示"请求本身不合法"（如资源不存在），
    //  这类 message 是可公开的业务提示，直接返回 400；不会走兜底"系统内部异常"。
    // ================================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<String> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("业务参数异常：{}", e.getMessage());
        return Result.error(400, e.getMessage() != null ? e.getMessage() : "请求参数不合法");
    }

    // ================================================================
    //  静态资源 404：纯 API 服务无首页/图标，浏览器直接访问 / 或 /favicon.ico 会触发。
    //  属正常 404，不按系统异常打印 ERROR 堆栈，避免控制台噪音。
    // ================================================================

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<String> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        log.debug("静态资源不存在 [{} {}]", request.getMethod(), request.getRequestURI());
        return Result.error(404, "资源不存在");
    }

    // ================================================================
    //  SSE 流式 / 异步：客户端主动断开连接（关页面、刷新、中止请求）时，
    //  Spring MVC 会抛 AsyncRequestNotUsableException 或 ClientAbortException。
    //  这是"客户端已走、无人在听"的正常场景，不能走兜底去往已断开的流写 Result（会二次报错）。
    //  这里只记 warn 日志并返回 null（不渲染响应体）。
    // ================================================================

    @ExceptionHandler(value = {AsyncRequestNotUsableException.class, ClientAbortException.class})
    public void handleClientAbort(IOException e, HttpServletResponse response,
                                  HttpServletRequest request) {
        log.warn("SSE/异步响应客户端中断 [{} {}]: {}",
                request.getMethod(), request.getRequestURI(), e.getMessage());
        // 空方法体：客户端已断开，任何响应写入都无意义且可能二次抛异常
    }

    // ================================================================
    //  兜底：所有未显式处理的异常。这里需要打印完整堆栈，方便排查业务 bug
    // ================================================================

    @ExceptionHandler(Exception.class)
    public Result<String> handleOther(Exception e, HttpServletRequest request) {
        // 完整堆栈只写服务端日志，便于排查业务 bug
        log.error("系统异常 [{} {}]", request.getMethod(), request.getRequestURI(), e);
        // 安全加固：不把内部异常原文（可能含 SQL/路径/堆栈）回给客户端，避免信息泄露
        return Result.error("系统内部异常，请稍后重试");
    }
}