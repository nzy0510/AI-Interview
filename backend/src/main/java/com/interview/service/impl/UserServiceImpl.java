package com.interview.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.interview.dto.LoginDTO;
import com.interview.dto.RegisterDTO;
import com.interview.dto.ResetPasswordDTO;
import com.interview.entity.User;
import com.interview.mapper.UserMapper;
import com.interview.service.EmailService;
import com.interview.service.UserService;
import com.interview.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public String login(LoginDTO loginDTO) {
        String loginIdentity = loginDTO.getUsername();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(User::getEmail, loginIdentity).or().eq(User::getUsername, loginIdentity));
        User user = this.getOne(wrapper);

        if (user == null) {
            throw new RuntimeException("邮箱或用户名不存在");
        }

        String md5Password = DigestUtil.md5Hex(loginDTO.getPassword());
        if (!user.getPassword().equals(md5Password) && !user.getPassword().equals(loginDTO.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("username", user.getUsername());

        return jwtUtils.generateJwt(claims);
    }

    @Override
    public void register(RegisterDTO registerDTO) {
        // 1. 验证邮箱验证码
        if (!emailService.verifyCode(registerDTO.getEmail(), registerDTO.getCode())) {
            throw new RuntimeException("验证码无效或已失效，请重新获取");
        }

        // 2. 检查用户名唯一
        LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
        usernameWrapper.eq(User::getUsername, registerDTO.getUsername());
        if (this.count(usernameWrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 3. 检查邮箱唯一
        LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(User::getEmail, registerDTO.getEmail());
        if (this.count(emailWrapper) > 0) {
            throw new RuntimeException("该邮箱已被注册");
        }

        // 4. 创建用户
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(DigestUtil.md5Hex(registerDTO.getPassword()));
        user.setEmail(registerDTO.getEmail());
        user.setNickname("User_" + System.currentTimeMillis() % 10000);

        this.save(user);
    }

    @Override
    public void sendVerificationCode(String email, String purpose) {
        emailService.sendVerificationCode(email, purpose);
    }

    @Override
    public void forgotPassword(String email) {
        // 检查邮箱是否存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        User user = this.getOne(wrapper);
        if (user == null) {
            throw new RuntimeException("该邮箱未注册");
        }
        emailService.sendVerificationCode(email, "重置密码");
    }

    @Override
    public void resetPassword(ResetPasswordDTO resetDTO) {
        // 1. 验证验证码
        if (!emailService.verifyCode(resetDTO.getEmail(), resetDTO.getCode())) {
            throw new RuntimeException("验证码无效或已失效，请重新获取");
        }

        // 2. 查找用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, resetDTO.getEmail());
        User user = this.getOne(wrapper);
        if (user == null) {
            throw new RuntimeException("该邮箱未注册");
        }

        // 3. 更新密码
        user.setPassword(DigestUtil.md5Hex(resetDTO.getNewPassword()));
        this.updateById(user);
    }
}
