package com.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.entity.User;
import com.interview.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AdminRoleService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    private final UserMapper userMapper;

    public AdminRoleService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && ROLE_ADMIN.equalsIgnoreCase(user.getRole());
    }

    public void requireAdmin(Long userId) {
        if (!isAdmin(userId)) {
            throw new RuntimeException("无权访问管理数据");
        }
    }

    @Transactional
    public User grantAdmin(Long targetUserId, Long operatorUserId) {
        requireAdmin(operatorUserId);
        User target = requireUser(targetUserId);
        target.setRole(ROLE_ADMIN);
        target.setAdminGrantedBy(operatorUserId);
        target.setAdminGrantedAt(LocalDateTime.now());
        userMapper.updateById(target);
        return target;
    }

    @Transactional
    public User revokeAdmin(Long targetUserId, Long operatorUserId) {
        requireAdmin(operatorUserId);
        User target = requireUser(targetUserId);
        if (ROLE_ADMIN.equalsIgnoreCase(target.getRole()) && adminCount() <= 1) {
            throw new RuntimeException("至少保留一个管理员");
        }
        target.setRole(ROLE_USER);
        target.setAdminGrantedBy(null);
        target.setAdminGrantedAt(null);
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", targetUserId)
                .set("role", ROLE_USER)
                .set("admin_granted_by", null)
                .set("admin_granted_at", null));
        return target;
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户不存在");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }

    private long adminCount() {
        Long count = userMapper.selectCount(new QueryWrapper<User>().eq("role", ROLE_ADMIN));
        return count == null ? 0 : count;
    }
}
