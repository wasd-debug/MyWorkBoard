package com.salarytracker.ai.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AiCredentialCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;

    public AiCredentialCipher(@Value("${app.ai.credential-key:}") String secret) {
        this.key = secret == null || secret.isBlank() ? null : sha256(secret);
    }

    public boolean available() { return key != null; }

    public String encrypt(String value) {
        if (!available()) throw new IllegalStateException("服务端尚未配置 AI_CREDENTIAL_KEY");
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(iv) + "." +
                    Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("API Key 加密失败", exception);
        }
    }

    public String decrypt(String value) {
        if (!available()) throw new IllegalStateException("服务端尚未配置 AI_CREDENTIAL_KEY");
        try {
            String[] parts = value.split("\\.");
            if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException("密文版本不支持");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128,
                    Base64.getUrlDecoder().decode(parts[1])));
            return new String(cipher.doFinal(Base64.getUrlDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("API Key 解密失败", exception);
        }
    }

    public String fingerprint(String value) {
        return hex(sha256(value == null ? "" : value));
    }

    private byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }
}
