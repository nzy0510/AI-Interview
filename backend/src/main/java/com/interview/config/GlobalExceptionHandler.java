package com.interview.config;

import com.interview.common.Result;
import com.interview.exception.QuotaExceededException;
import com.interview.exception.RateLimitExceededException;
import com.interview.service.AppEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：拦截所有 Controller 抛出的异常，统一返回前端可识别的 Result 格式
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired(required = false)
    private AppEventService appEventService;

    @ExceptionHandler(RateLimitExceededException.class)
    public Result<String> handleRateLimit(RateLimitExceededException e, HttpServletResponse response,
                                          HttpServletRequest request) {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
        recordException(request, "RATE_LIMIT", e.getMessage());
        return Result.error(429, e.getMessage());
    }

    @ExceptionHandler(QuotaExceededException.class)
    public Result<String> handleQuotaExceeded(QuotaExceededException e, HttpServletResponse response,
                                              HttpServletRequest request) {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        recordException(request, "QUOTA_EXCEEDED", e.getMessage());
        return Result.error(429, e.getMessage());
    }

    /**
     * 捕获鉴权相关异常（如 "未登录" "Token过期" 等）
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e, HttpServletResponse response,
                                                 HttpServletRequest request) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("未登录") || msg.contains("Token"))
                && !msg.contains("验证码")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            recordException(request, "UNAUTHORIZED", msg);
            return Result.error(401, msg);
        }
        if (msg != null && msg.contains("无权访问")) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            recordException(request, "FORBIDDEN", msg);
            return Result.error(403, msg);
        }
        e.printStackTrace();
        recordException(request, "RUNTIME_EXCEPTION", msg);
        return Result.error(msg);
    }

    /**
     * 捕获所有未处理的异常（兜底方案）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleException(Exception e, HttpServletRequest request) {
        e.printStackTrace();
        recordException(request, "API_EXCEPTION", e.getMessage());
        return Result.error(e.getMessage());
    }

    /**
     * 捕获参数校验异常（如 @NotBlank 等注解触发时）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleValidationException(MethodArgumentNotValidException e,
                                                    HttpServletRequest request) {
        recordException(request, "VALIDATION_ERROR", e.getMessage());
        return Result.error(400, e.getBindingResult().getAllErrors().get(0).getDefaultMessage());
    }

    private void recordException(HttpServletRequest request, String code, String message) {
        if (appEventService == null) return;
        appEventService.recordSystemEvent(null, code, "system",
                java.util.Map.of(
                        "path", request != null ? request.getRequestURI() : "",
                        "message", message != null ? message : ""
                ),
                false,
                message);
    }
}
