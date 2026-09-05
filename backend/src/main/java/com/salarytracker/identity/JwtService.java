package com.salarytracker.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class JwtService {
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long accessTtlSeconds;

    public JwtService(ObjectMapper objectMapper,
                      @Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-ttl:900}") long accessTtlSeconds) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("JWT secret 至少需要 32 字节");
        }
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String createAccessToken(CurrentUser user) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.id());
        claims.put("username", user.username());
        claims.put("nickname", user.nickname());
        claims.put("authorities", user.authorities());
        claims.put("iat", now);
        claims.put("exp", now + accessTtlSeconds);
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(json(Map.of("alg", "HS256", "typ", "JWT")).getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json(claims).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + signature(header, payload);
    }

    public CurrentUser parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !MessageDigest.isEqual(signature(parts[0], parts[1]).getBytes(StandardCharsets.US_ASCII), parts[2].getBytes(StandardCharsets.US_ASCII))) {
                return null;
            }
            JsonNode payload = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            if (payload.path("exp").asLong(0) <= Instant.now().getEpochSecond()) return null;
            Set<String> authorities = new java.util.HashSet<>();
            payload.path("authorities").forEach(node -> authorities.add(node.asText()));
            return new CurrentUser(payload.path("sub").asLong(), payload.path("username").asText(), payload.path("nickname").asText(""), authorities);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String signature(String encodedHeader, String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String input = encodedHeader + "." + encodedPayload;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法签发 token", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法编码 token", exception);
        }
    }
}
