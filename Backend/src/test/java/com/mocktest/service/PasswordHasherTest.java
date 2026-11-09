package com.mocktest.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    @Test
    void correctPasswordVerifies() {
        String stored = PasswordHasher.hash("secret123");
        assertTrue(PasswordHasher.verify("secret123", stored));
    }

    @Test
    void wrongPasswordDoesNotVerify() {
        String stored = PasswordHasher.hash("secret123");
        assertFalse(PasswordHasher.verify("secret124", stored));
        assertFalse(PasswordHasher.verify("", stored));
    }

    @Test
    void hashIsSaltedSoSamePasswordGivesDifferentHashes() {
        String a = PasswordHasher.hash("secret123");
        String b = PasswordHasher.hash("secret123");
        assertNotEquals(a, b);
        assertTrue(PasswordHasher.verify("secret123", a));
        assertTrue(PasswordHasher.verify("secret123", b));
    }

    @Test
    void hashHasSaltAndDigestParts() {
        String stored = PasswordHasher.hash("secret123");
        String[] parts = stored.split(":");
        assertEquals(2, parts.length);
        assertFalse(stored.contains("secret123"));
    }
}
