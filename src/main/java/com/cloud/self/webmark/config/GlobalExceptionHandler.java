package com.cloud.self.webmark.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 全局异常处理器，统一返回 JSON 格式错误响应。
 * 所有 @RestController / @ResponseBody 接口的异常由此统一处理，
 * 替代在各 Controller 中分散的 try-catch。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("参数错误 [{}]: {}", request.getRequestURI(), e.getMessage());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException e, HttpServletRequest request) {
        log.warn("资源不存在 [{}]: {}", request.getRequestURI(), e.getMessage());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", "请求的资源不存在");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    /**
     * 客户端提前断开连接（如下载文件时取消），不是服务器错误，仅 debug 日志记录。
     */
    @ExceptionHandler(org.apache.catalina.connector.ClientAbortException.class)
    public void handleClientAbort(org.apache.catalina.connector.ClientAbortException e, HttpServletRequest request) {
        log.debug("客户端断开连接 [{}]: {}", request.getRequestURI(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e, HttpServletRequest request) {
        log.error("请求处理异常 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", "服务器内部错误");
        return ResponseEntity.internalServerError().body(result);
    }
}
