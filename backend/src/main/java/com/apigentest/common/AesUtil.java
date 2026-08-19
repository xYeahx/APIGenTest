package com.apigentest.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感配置加密工具：AES-256-GCM，密钥由 app.crypto-key 派生（SHA-256）。
 * 密文格式：enc:v1: 前缀 + base64(iv + 密文)，便于识别版本并兼容历史明文。
 */
@Component
public class AesUtil {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final String DEFAULT_KEY = "apigentest-dev-crypto-key-2026";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesUtil(@Value("${app.crypto-key:apigentest-dev-crypto-key-2026}") String cryptoKey) {
        String material = (cryptoKey == null || cryptoKey.isBlank()) ? DEFAULT_KEY : cryptoKey;
        this.key = new SecretKeySpec(sha256(material), "AES");
    }

    /** 加密明文，返回带 enc:v1: 前缀的密文串 */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("敏感配置加密失败", e);
        }
    }

    /** 解密带前缀的密文；非加密值原样返回；解密失败返回 null（调用方按未配置处理） */
    public String decrypt(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            return value;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(combined, 0, iv, 0, IV_LEN);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(combined, IV_LEN, combined.length - IV_LEN);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("加密密钥派生失败", e);
        }
    }
}
