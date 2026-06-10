package com.interview.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ApiKeyCryptoService {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyCryptoService(@Value("${app.llm-config.encryption-key:${APP_LLM_CONFIG_ENCRYPTION_KEY:}}") String secret) {
        if (secret == null || secret.isBlank() || secret.trim().length() < 16) {
            throw new IllegalStateException("APP_LLM_CONFIG_ENCRYPTION_KEY 至少需要 16 个字符，用于加密用户大模型 API Key");
        }
        this.keySpec = new SecretKeySpec(sha256(secret.trim()), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            throw new IllegalArgumentException("加密 API Key 为空");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encrypted);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 解密失败", e);
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("加密密钥初始化失败", e);
        }
    }
}
