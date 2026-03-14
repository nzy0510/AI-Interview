package com.interview.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类：提供 Token 的生成与解析
 * JWT (JSON Web Token) 用于无状态的用户身份验证，前端登录成功后获得 Token，
 * 后续请求携带此 Token 即可证明身份，无需服务器存储 Session
 */
public class JwtUtils {
    private static final String SIGN_KEY = "ai_interview_secret_key_123456"; // 签名密钥（生产环境应从配置文件读取）
    private static final long EXPIRE = 1000 * 60 * 60 * 24; // Token 有效期：24小时

    /**
     * 生成 JWT Token
     * @param claims 负载信息，通常包含用户 ID 和用户名
     * @return 签名后的 JWT 字符串
     */
    public static String generateJwt(Map<String, Object> claims){
        return Jwts.builder()
                .addClaims(claims)                                        // 写入负载数据
                .signWith(SignatureAlgorithm.HS256, SIGN_KEY)             // HS256 算法签名
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE)) // 设置过期时间
                .compact();
    }

    /**
     * 解析 JWT Token，获取其中的负载信息
     * @param jwt 前端传入的 Token 字符串
     * @return Claims 对象，可通过 get("id") 等方法提取用户信息
     */
    public static Claims parseJwt(String jwt){
        return Jwts.parser()
                .setSigningKey(SIGN_KEY)           // 使用同一密钥验签
                .parseClaimsJws(jwt)               // 解析并验证签名是否有效
                .getBody();
    }
}
