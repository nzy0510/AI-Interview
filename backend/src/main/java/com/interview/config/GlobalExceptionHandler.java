package com.interview.config;

import com.interview.common.Result;
import jakarta.servlet.http.HttpServletResponse;
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

    /**
     * 捕获鉴权相关异常（如 "未登录" "Token过期" 等）
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e, HttpServletResponse response) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("未登录") || msg.contains("Token"))
                && !msg.contains("验证码")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return Result.error(401, msg);
        }
        e.printStackTrace();
        return Result.error(msg);
    }

    /**
     * 捕获所有未处理的异常（兜底方案）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleException(Exception e) {
        e.printStackTrace();
        return Result.error(e.getMessage());
    }

    /**
     * 捕获参数校验异常（如 @NotBlank 等注解触发时）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        return Result.error(400, e.getBindingResult().getAllErrors().get(0).getDefaultMessage());
    }
}
