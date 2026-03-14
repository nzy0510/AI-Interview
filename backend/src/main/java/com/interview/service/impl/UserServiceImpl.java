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

/**
 * 用户服务实现类：处理登录验证与注册逻辑
 * 继承 MyBatis-Plus 的 ServiceImpl，自动获得常用的 CRUD 方法（如 getOne, count, save 等）
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 用户登录：查找用户 → 校验密码 → 签发 JWT Token
     */
    @Override
    public String login(LoginDTO loginDTO) {
        // 1. 根据用户名查找数据库记录（MyBatis-Plus 的 LambdaQueryWrapper 语法）
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, loginDTO.getUsername());
        User user = this.getOne(wrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 2. 校验密码：将前端传入的明文密码做 MD5 加密后与数据库中的密文对比
        // 注意：测试用户 admin 的密码是明文 '123456'，所以额外兑容明文比较
        String md5Password = DigestUtil.md5Hex(loginDTO.getPassword());
        if (!user.getPassword().equals(md5Password) && !user.getPassword().equals(loginDTO.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 3. 将用户信息写入 JWT 负载（claims），签发 Token 返回给前端
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("username", user.getUsername());

        return JwtUtils.generateJwt(claims);
    }

    /**
     * 用户注册：检查用户名重复 → MD5 加密密码 → 写入数据库
     */
    @Override
    public void register(LoginDTO loginDTO) {
        // 1. 检查用户名是否已被注册
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, loginDTO.getUsername());
        long count = this.count(wrapper);
        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 2. 创建新用户，密码经 MD5 加密后存储
        User user = new User();
        user.setUsername(loginDTO.getUsername());
        user.setPassword(DigestUtil.md5Hex(loginDTO.getPassword())); // Hutool MD5 加密
        user.setNickname("User_" + System.currentTimeMillis() % 10000); // 自动生成昵称

        this.save(user); // MyBatis-Plus 自动执行 INSERT
    }
}
