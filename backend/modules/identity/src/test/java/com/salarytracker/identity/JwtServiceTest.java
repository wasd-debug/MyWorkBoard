package com.salarytracker.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    @Test
    void signsAndVerifiesAccessToken() throws Exception {
        JwtService service = new JwtService(new ObjectMapper(), "01234567890123456789012345678901", 7200);
        CurrentUser user = new CurrentUser(7L, "alice", "Alice", Set.of("ROLE_USER", "worktime:read"));
        String token = service.createAccessToken(user);

        assertThat(service.parse(token)).isEqualTo(user);
        assertThat(service.parse(token + "tampered")).isNull();
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        var claims = new ObjectMapper().readTree(payload);
        assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(7200);
    }
}
