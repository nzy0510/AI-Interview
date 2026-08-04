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

        String username = AuthModeProperties.LOCAL_ADMIN_USERNAME;
        String password = AuthModeProperties.LOCAL_ADMIN_PASSWORD;

        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (existing != null) {
            if (!AdminRoleService.ROLE_ADMIN.equalsIgnoreCase(existing.getRole())) {
                throw new IllegalStateException(
                        "本地默认用户名 admin 已被同名账号占用，但该账号不是 ADMIN；请处理数据库冲突后重启");
            }
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
}
