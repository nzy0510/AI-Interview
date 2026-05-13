package com.interview.controller;

import com.interview.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${question-bank.qdrant.enabled:true}")
    private boolean qdrantEnabled;

    @Value("${question-bank.qdrant.url:http://localhost:6333}")
    private String qdrantUrl;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("app", "UP");
        checks.put("mysql", mysqlStatus());
        checks.put("redis", redisStatus());
        checks.put("qdrant", qdrantStatus());
        checks.put("status", checks.containsValue("DOWN") ? "DEGRADED" : "UP");
        return Result.success(checks);
    }

    private String mysqlStatus() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1 ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String redisStatus() {
        if (stringRedisTemplate == null) return "UNKNOWN";
        try {
            String pong = stringRedisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String qdrantStatus() {
        if (!qdrantEnabled) return "DISABLED";
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(1000);
            factory.setReadTimeout(1000);
            RestTemplate restTemplate = new RestTemplate(factory);
            restTemplate.getForObject(qdrantUrl + "/healthz", String.class);
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
