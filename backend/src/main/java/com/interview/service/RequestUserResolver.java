package com.interview.service;

import com.interview.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class RequestUserResolver {

    private final JwtUtils jwtUtils;

    public RequestUserResolver(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    public Long resolveUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("currentUserId");
        if (attr instanceof Number n) {
            return n.longValue();
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            token = request.getParameter("token");
        }
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            Claims claims = jwtUtils.parseJwt(token);
            return ((Number) claims.get("id")).longValue();
        } catch (Exception ignored) {
            return null;
        }
    }
}
