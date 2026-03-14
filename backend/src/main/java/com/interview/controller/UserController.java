package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.LoginDTO;
import com.interview.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器：提供登录和注册两个基础接口
 * - POST /login 用户登录，成功后返回 JWT Token
 * - POST /register 用户注册，将账号密码写入数据库
 */
@RestController
@RequestMapping("/api/user")
@CrossOrigin // 允许跨域请求，便于 Vue 前端调用
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户登录：验证用户名密码，成功后签发 JWT Token
     * 
     * @param loginDTO 包含 username 和 password 的请求体（通过 @Validated 自动校验非空）
     * @return JWT Token 字符串，前端保存到 localStorage 用于后续接口鉴权
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody @Validated LoginDTO loginDTO) {
        String token = userService.login(loginDTO);
        return Result.success(token);
    }

    /**
     * 用户注册：将新用户信息写入数据库，密码会进行 MD5 加密存储
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody @Validated LoginDTO loginDTO) {
        userService.register(loginDTO);
        return Result.success("注册成功");
    }
}
