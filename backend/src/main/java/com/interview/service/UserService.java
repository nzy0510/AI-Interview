package com.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.interview.dto.LoginDTO;
import com.interview.dto.RegisterDTO;
import com.interview.dto.ResetPasswordDTO;
import com.interview.entity.User;
import com.interview.entity.UserPreference;
import org.springframework.web.multipart.MultipartFile;

public interface UserService extends IService<User> {
    
    String login(LoginDTO loginDTO);
    
    void register(RegisterDTO registerDTO);

    void sendVerificationCode(String email, String purpose);

    void forgotPassword(String email);

    void resetPassword(ResetPasswordDTO resetDTO);

    void updateProfile(Long userId, String nickname, String email);

    void changePassword(Long userId, String oldPassword, String newPassword);

    UserPreference getPreference(Long userId);

    void updatePreference(Long userId, UserPreference pref);

    void updateAvatar(Long userId, String avatarUrl);

    String uploadAvatar(Long userId, MultipartFile file);
}
