package com.interview.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.interview.dto.LoginDTO;
import com.interview.entity.User;
import com.interview.mapper.UserMapper;
import com.interview.service.UserService;
import com.interview.utils.JwtUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public String login(LoginDTO loginDTO) {
        // Find user by username
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, loginDTO.getUsername());
        User user = this.getOne(wrapper);
        
        if (user == null) {
            throw new RuntimeException("User does not exist");
        }
        
        // Check password (MD5 encrypted for simplicity here, can use BCrypt later)
        // Note: For dummy test user, we allow plain '123456' comparison if MD5 doesn't match, just for easy local testing
        String md5Password = DigestUtil.md5Hex(loginDTO.getPassword());
        if (!user.getPassword().equals(md5Password) && !user.getPassword().equals(loginDTO.getPassword())) {
            throw new RuntimeException("Incorrect password");
        }
        
        // Issue JWT token
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("username", user.getUsername());
        
        return JwtUtils.generateJwt(claims);
    }

    @Override
    public void register(LoginDTO loginDTO) {
        // Check if username exists
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, loginDTO.getUsername());
        long count = this.count(wrapper);
        if (count > 0) {
            throw new RuntimeException("Username already exists");
        }
        
        // Create new user
        User user = new User();
        user.setUsername(loginDTO.getUsername());
        user.setPassword(DigestUtil.md5Hex(loginDTO.getPassword())); // Encrypt password
        user.setNickname("User_" + System.currentTimeMillis() % 10000);
        
        this.save(user);
    }
}
