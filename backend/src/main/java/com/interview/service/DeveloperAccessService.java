package com.interview.service;

import com.interview.entity.User;
import com.interview.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeveloperAccessService {

    private final UserMapper userMapper;
    private final ConcurrentMap<Long, Boolean> developerCache = new ConcurrentHashMap<>();

    @Value("${app.developer.exempt-user-ids:}")
    private String exemptUserIds;

    @Value("${app.developer.exempt-usernames:}")
    private String exemptUsernames;

    @Value("${app.developer.exempt-emails:}")
    private String exemptEmails;

    public DeveloperAccessService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public boolean isDeveloper(Long userId) {
        if (userId == null) {
            return false;
        }
        return developerCache.computeIfAbsent(userId, this::loadDeveloperFlag);
    }

    private boolean loadDeveloperFlag(Long userId) {
        if (tokens(exemptUserIds).contains(String.valueOf(userId))) {
            return true;
        }

        try {
            User user = userMapper.selectById(userId);
            if (user == null) {
                return false;
            }
            return tokens(exemptUsernames).contains(normalize(user.getUsername()))
                    || tokens(exemptEmails).contains(normalize(user.getEmail()));
        } catch (Exception e) {
            log.trace("开发者白名单检查跳过 userId={}: {}", userId, e.getMessage());
            return false;
        }
    }

    private Set<String> tokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(this::normalize)
                .filter(token -> !token.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
