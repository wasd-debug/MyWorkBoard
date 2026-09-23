package com.salarytracker.ai.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiCredentialCipherTest {
    @Test
    void encryptsWithRandomIvAndDecryptsRoundTrip() {
        AiCredentialCipher cipher = new AiCredentialCipher("a-dedicated-test-secret");

        String first = cipher.encrypt("sk-sensitive-value");
        String second = cipher.encrypt("sk-sensitive-value");

        assertNotEquals(first, second);
        assertFalse(first.contains("sk-sensitive-value"));
        assertEquals("sk-sensitive-value", cipher.decrypt(first));
        assertEquals(cipher.fingerprint("sk-sensitive-value"), cipher.fingerprint("sk-sensitive-value"));
    }

    @Test
    void refusesToStoreCredentialsWithoutDedicatedKey() {
        AiCredentialCipher cipher = new AiCredentialCipher("");

        assertThrows(IllegalStateException.class, () -> cipher.encrypt("sk-value"));
    }
}
