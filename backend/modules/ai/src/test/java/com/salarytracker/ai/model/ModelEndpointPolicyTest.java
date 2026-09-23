package com.salarytracker.ai.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelEndpointPolicyTest {
    @Test
    void acceptsPublicHttpsEndpoint() {
        ModelEndpointPolicy policy = new ModelEndpointPolicy(false);

        assertEquals("https://api.deepseek.com/chat/completions",
                policy.validate("https://api.deepseek.com/chat/completions").toString());
    }

    @Test
    void rejectsPlainHttpCredentialsAndPrivateTargets() {
        ModelEndpointPolicy policy = new ModelEndpointPolicy(false);

        assertThrows(IllegalArgumentException.class, () -> policy.validate("http://api.example.com/v1/chat"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://user:pass@example.com/v1/chat"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://127.0.0.1/v1/chat"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://localhost/v1/chat"));
    }
}
