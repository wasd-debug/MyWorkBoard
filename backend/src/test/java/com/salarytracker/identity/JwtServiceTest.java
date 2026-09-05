package com.salarytracker.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    @Test
    void signsAndVerifiesAccessToken() {
        JwtService service = new JwtService(new ObjectMapper(), "01234567890123456789012345678901", 900);
        CurrentUser user = new CurrentUser(7L, "alice", "Alice", Set.of("ROLE_USER", "worktime:read"));
        String token = service.createAccessToken(user);

        assertThat(service.parse(token)).isEqualTo(user);
        assertThat(service.parse(token + "tampered")).isNull();
    }
}
