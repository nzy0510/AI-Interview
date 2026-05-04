package com.interview.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 邮件验证码服务：发送验证码并管理其生命周期
 */
@Service
@Slf4j
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    // 验证码缓存：key = email, value = CodeEntry(code, expireTime)
    private final Map<String, CodeEntry> codeCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();

    private record CodeEntry(String code, long expireTime) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    public EmailService() {
        // 每分钟清理过期验证码
        cleaner.scheduleAtFixedRate(() -> {
            codeCache.entrySet().removeIf(e -> e.getValue().isExpired());
        }, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * 发送验证码到指定邮箱
     * 
     * @param toEmail 目标邮箱
     * @param purpose 用途描述（如"注册"或"重置密码"）
     */
    public void sendVerificationCode(String toEmail, String purpose) {
        if (mailSender == null || fromEmail.isBlank()) {
            throw new RuntimeException("邮件服务未配置，请在 .env 中设置 MAIL_USERNAME 和 MAIL_PASSWORD");
        }

        // 频率限制：同一邮箱60秒内不可重复发送
        CodeEntry existing = codeCache.get(toEmail);
        if (existing != null && !existing.isExpired()
                && (existing.expireTime - System.currentTimeMillis()) > 4 * 60 * 1000) {
            throw new RuntimeException("验证码发送太频繁，请60秒后再试");
        }

        String code = String.format("%06d", random.nextInt(1000000));
        codeCache.put(toEmail, new CodeEntry(code, System.currentTimeMillis() + 5 * 60 * 1000));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("【AI面试平台】" + purpose + "验证码");
        message.setText("您好！\n\n您的验证码为：" + code + "\n\n该验证码5分钟内有效，请勿泄露给他人。\n\n——InterWise");

        try {
            mailSender.send(message);
            log.info("验证码已发送至 {}", toEmail);
        } catch (MailException e) {
            codeCache.remove(toEmail);
            log.error("邮件发送失败: {}", rootCauseMessage(e), e);
            throw new RuntimeException(classifyMailError(e));
        }
    }

    /**
     * 验证验证码是否正确
     */
    public boolean verifyCode(String email, String code) {
        CodeEntry entry = codeCache.get(email);
        if (entry == null || entry.isExpired()) {
            return false;
        }
        if (entry.code.equals(code)) {
            codeCache.remove(email); // 验证成功后立即失效
            return true;
        }
        return false;
    }

    private String classifyMailError(MailException e) {
        Throwable root = rootCause(e);
        String message = root.getMessage() != null ? root.getMessage() : e.getMessage();
        String lowerMessage = message != null ? message.toLowerCase() : "";

        if (e instanceof MailAuthenticationException || lowerMessage.contains("auth")
                || lowerMessage.contains("authentication") || lowerMessage.contains("535")
                || lowerMessage.contains("login")) {
            return "邮件服务认证失败，请检查 MAIL_USERNAME 是否为发件邮箱，MAIL_PASSWORD 是否为 SMTP 授权码";
        }
        if (lowerMessage.contains("could not connect") || lowerMessage.contains("connection")
                || lowerMessage.contains("timed out") || lowerMessage.contains("timeout")
                || lowerMessage.contains("unknown smtp host")) {
            return "无法连接邮件服务器，请检查 MAIL_HOST、MAIL_PORT 以及当前网络是否允许 SMTP 连接";
        }
        if (e instanceof MailSendException || lowerMessage.contains("invalid addresses")
                || lowerMessage.contains("recipient") || lowerMessage.contains("550")) {
            return "邮件发送失败，请检查收件邮箱地址是否可接收邮件";
        }
        return "邮件发送失败：" + message;
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable root = rootCause(throwable);
        return root.getMessage() != null ? root.getMessage() : root.toString();
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
