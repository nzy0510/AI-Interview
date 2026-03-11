package com.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.interview.dto.LoginDTO;
import com.interview.entity.User;

public interface UserService extends IService<User> {
    
    // User login
    String login(LoginDTO loginDTO);
    
    // User registration
    void register(LoginDTO loginDTO);
}
