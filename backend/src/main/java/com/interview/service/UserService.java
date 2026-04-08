package com.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.interview.dto.LoginDTO;
import com.interview.dto.RegisterDTO;
import com.interview.dto.ResetPasswordDTO;
import com.interview.entity.User;

public interface UserService extends IService<User> {
    
    String login(LoginDTO loginDTO);
    
    void register(RegisterDTO registerDTO);

    void sendVerificationCode(String email, String purpose);

    void forgotPassword(String email);

    void resetPassword(ResetPasswordDTO resetDTO);
}
