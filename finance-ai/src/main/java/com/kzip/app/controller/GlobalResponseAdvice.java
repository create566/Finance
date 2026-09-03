package com.kzip.app.controller;


import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import reactor.core.publisher.Flux;

@RestControllerAdvice(basePackages = "com.kzip.app.controller")
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> type = returnType.getParameterType();

        // ---- 这些类型天生就不应该被统一包装成 Result ----
        // 注意：Spring 处理 Flux<T> / Mono<T> 时，对每个流元素单独调用 advice，
        //       传入的 returnType 是元素类型 T 而不是声明类型 Flux，
        //       所以 supports 阶段用 Flux.class.isAssignableFrom(type) 拦不住流式场景。
        //       流式/SSE 的兜底完全依赖 beforeBodyWrite 里的 Content-Type 判断。
        if (Flux.class.isAssignableFrom(type))                  return false;
        if (Resource.class.isAssignableFrom(type))              return false;
        if (ResponseEntity.class.isAssignableFrom(type))        return false;
        if (void.class == type || Void.class == type)           return false;
        if (Result.class.isAssignableFrom(type))                 return false;

        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {

        // ========== 第一道保险：非 application/json 兼容的响应一律不包装 ==========
        // 任何非 JSON 响应（text/html、text/plain、text/event-stream、application/octet-stream、
        // image/*、application/vnd.ms-excel 等）都不应该被 Result 包装。
        // 否则对应的 HttpMessageConverter 拿到 Result 对象会强转或查找失败：
        //   - StringHttpMessageConverter.addDefaultHeaders 强转 Result → String 抛 ClassCastException
        //   - SSE converter 找不到 Result + text/event-stream 的处理器抛 HttpMessageNotWritableException
        //   - ResourceHttpMessageConverter 强转 Result → Resource 抛 ClassCastException
        //
        // 关键背景：Spring 处理 Flux<T> / Mono<T> 时对每个流元素调用 advice，
        // returnType 是元素类型 T 而不是声明类型 Flux，supports 阶段无法用 Flux 类型识别跳过，
        // 必须在这里按 Content-Type 兜底拦下。
        if (selectedContentType != null
                && !MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }

        // 下面处理 JSON 响应的统一包装逻辑

        if (body == null) {
            // void / null 方法 → 包一层 Result.success(null)
            return Result.success(null);
        }
        // 已经是 Result（多半来自 GlobalExceptionHandler）→ 原样返回，不二次包装
        if (body instanceof Result) {
            return body;
        }
        // 文件/二进制流 → 原样写出（保险，理论上上面 Content-Type 判断已经拦下）
        if (body instanceof Resource) {
            return body;
        }
        // ResponseEntity → 框架后续还会读取 status/headers/body，不能动
        if (body instanceof ResponseEntity) {
            return body;
        }

        // ========== JSON 响应下 String 的特殊处理 ==========
        // 当方法声明返回 String 且 selectedContentType 是 application/json 时，
        // Spring 仍可能优先选中 StringHttpMessageConverter（它默认能处理 text/* 和 */*）。
        // 手动把 Content-Type 显式设成 application/json，让 Jackson converter 接管 Result 序列化。
        if (body instanceof String) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return Result.success(body);
        }

        // 其它对象类型（User/DTO/List/Map 等）→ 统一包装 Result，交给 Jackson 正常序列化
        return Result.success(body);
    }
}
