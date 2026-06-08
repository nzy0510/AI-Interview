package com.interview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.ConnectException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@DisplayName("EmailService — 邮件验证码")
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("验证码为：(\\d{6})");

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

    @Test
    @DisplayName("验证码多次错误后应锁定，正确验证码也不能继续使用")
    void shouldLockVerificationCodeAfterRepeatedFailures() {
        emailService.sendVerificationCode("target@example.com", "注册");
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String code = extractCode(captor.getValue().getText());

        for (int i = 0; i < 5; i++) {
            org.assertj.core.api.Assertions.assertThat(emailService.verifyCode("target@example.com", "000000"))
                    .isFalse();
        }

        org.assertj.core.api.Assertions.assertThat(emailService.verifyCode("target@example.com", code))
                .isFalse();
    }

    private String extractCode(String text) {
        Matcher matcher = CODE_PATTERN.matcher(text);
        org.assertj.core.api.Assertions.assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
