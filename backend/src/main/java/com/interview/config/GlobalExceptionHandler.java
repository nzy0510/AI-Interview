package com.interview.config;

import com.interview.common.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：拦截所有 Controller 抛出的异常，统一返回前端可识别的 Result 格式
 * 避免前端收到 500 原始报错页面，而是始终收到 {code: 500, msg: "错误描述"} 的 JSON
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获所有未处理的异常（兆底方案）
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        e.printStackTrace(); // 控制台输出堆栈信息，便于调试
        return Result.error(e.getMessage());
    }

    /**
     * 捕获参数校验异常（如 @NotBlank 等注解触发时）
     * 例如：用户名或密码为空时，会返回对应的校验提示消息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        return Result.error(e.getBindingResult().getAllErrors().get(0).getDefaultMessage());
    }
}
