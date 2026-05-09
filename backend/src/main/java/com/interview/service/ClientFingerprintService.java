package com.interview.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class ClientFingerprintService {

    @Value("${app.analytics.hash-salt:interwise-dev-hash-salt}")
    private String hashSalt;

    public String clientIp(HttpServletRequest request) {
        String forwarded = firstHeader(request, "X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = firstHeader(request, "X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    public String anonymousId(HttpServletRequest request) {
        String fromHeader = firstHeader(request, "X-Anonymous-Id");
        if (fromHeader == null || fromHeader.isBlank()) {
            fromHeader = request.getParameter("aid");
        }
        if (fromHeader == null) return null;
        String cleaned = fromHeader.replaceAll("[^A-Za-z0-9_-]", "");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    public String ipHash(HttpServletRequest request) {
        return sha256(clientIp(request));
    }

    public String userAgentHash(HttpServletRequest request) {
        String ua = firstHeader(request, "User-Agent");
        return ua == null || ua.isBlank() ? null : sha256(ua);
    }

    public String sha256(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((hashSalt + ":" + value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private String firstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value != null && !value.isBlank() ? value : null;
    }
}
