package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.LoginDTO;
import com.interview.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@CrossOrigin // Allow cross-origin requests from Vue frontend
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result<String> login(@RequestBody @Validated LoginDTO loginDTO) {
        String token = userService.login(loginDTO);
        return Result.success(token);
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody @Validated LoginDTO loginDTO) {
        userService.register(loginDTO);
        return Result.success("Registration successful");
    }
}
