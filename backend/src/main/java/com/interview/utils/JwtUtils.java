package com.interview.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * JWT 工具：提供 Token 生成与解析，签名密钥通过 Spring 配置注入。
 */
@Component
public class JwtUtils {

    private String signKey;
    private long expireMs = 86_400_000; // 24h default

    /**
     * Spring 构造器注入。生产配置来自 application.yml 的 jwt.sign-key 和 jwt.expire-ms。
     */
    public JwtUtils(
            @Value("${jwt.sign-key}") String signKey,
            @Value("${jwt.expire-ms:86400000}") long expireMs) {
        this.signKey = signKey;
        this.expireMs = expireMs;
    }

    /** 仅用于单元测试注入 */
    void setSignKey(String signKey) {
        this.signKey = signKey;
    }

    void setExpireMs(long expireMs) {
        this.expireMs = expireMs;
    }

    public String generateJwt(Map<String, Object> claims) {
        return Jwts.builder()
                .addClaims(claims)
                .signWith(SignatureAlgorithm.HS256, signKey)
                .setExpiration(new Date(System.currentTimeMillis() + expireMs))
                .compact();
    }

    public Claims parseJwt(String jwt) {
        return Jwts.parser()
                .setSigningKey(signKey)
                .parseClaimsJws(jwt)
                .getBody();
    }
}
