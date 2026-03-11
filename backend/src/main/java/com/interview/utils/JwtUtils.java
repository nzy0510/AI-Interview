package com.interview.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import java.util.Map;

public class JwtUtils {
    private static final String SIGN_KEY = "ai_interview_secret_key_123456";
    private static final long EXPIRE = 1000 * 60 * 60 * 24; // 1 day

    // Generate JWT
    public static String generateJwt(Map<String, Object> claims){
        return Jwts.builder()
                .addClaims(claims)
                .signWith(SignatureAlgorithm.HS256, SIGN_KEY)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE))
                .compact();
    }

    // Parse JWT
    public static Claims parseJwt(String jwt){
        return Jwts.parser()
                .setSigningKey(SIGN_KEY)
                .parseClaimsJws(jwt)
                .getBody();
    }
}
