package com.interview.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.config.AuthModeProperties;
import com.interview.entity.User;
import com.interview.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class LocalAdminInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final AuthModeProperties properties;

    public LocalAdminInitializer(UserMapper userMapper, AuthModeProperties properties) {
        this.userMapper = userMapper;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isLocalAdminMode()) {
            return;
        }

        String username = properties.getLocalAdmin().getUsername();
        String password = properties.getLocalAdmin().getPassword();
        validateCredentials(username, password);

        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (existing != null) {
            log.info("本地管理员账号已存在，保留现有密码与资料: {}", username);
            return;
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        admin.setNickname("本地管理员");
        admin.setRole(AdminRoleService.ROLE_ADMIN);
        admin.setAdminGrantedAt(LocalDateTime.now());
        userMapper.insert(admin);
        log.info("本地管理员账号初始化完成: {}", username);
    }

    private void validateCredentials(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("APP_LOCAL_ADMIN_USERNAME 不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalStateException("APP_LOCAL_ADMIN_PASSWORD 至少需要 6 位");
        }
    }
}
