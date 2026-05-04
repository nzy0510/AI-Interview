package com.interview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@DisplayName("EmailService — 邮件验证码")
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService();
        ReflectionTestUtils.setField(emailService, "mailSender", mailSender);
        ReflectionTestUtils.setField(emailService, "fromEmail", "sender@qq.com");
    }

    @Test
    @DisplayName("SMTP 认证失败时提示检查发件邮箱和授权码")
    void shouldExplainAuthenticationFailure() {
        doThrow(new MailAuthenticationException("535 Login Fail"))
                .when(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        assertThatThrownBy(() -> emailService.sendVerificationCode("target@example.com", "注册"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("认证失败")
                .hasMessageContaining("SMTP 授权码");
    }

    @Test
    @DisplayName("SMTP 连接失败时提示检查服务器、端口和网络")
    void shouldExplainConnectionFailure() {
        doThrow(new MailSendException("send failed", new ConnectException("Connection timed out")))
                .when(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        assertThatThrownBy(() -> emailService.sendVerificationCode("target@example.com", "注册"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无法连接邮件服务器");
    }
}
