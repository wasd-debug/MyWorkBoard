package com.salarytracker.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class LegacyAdminBootstrap {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String password;

    public LegacyAdminBootstrap(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
                                @Value("${app.legacy-admin-password:}") String password) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        if (password == null || password.isBlank()) return;
        try {
            jdbcTemplate.update("UPDATE app_user SET password_hash = ? WHERE id = 1 AND password_hash = '!'",
                    passwordEncoder.encode(password));
        } catch (Exception ignored) {
        }
    }
}
