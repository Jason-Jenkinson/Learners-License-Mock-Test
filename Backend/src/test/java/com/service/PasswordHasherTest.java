package com.mocktest.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    @Test
    void hash_thenVerify_withSamePassword_succeeds() {
        String stored = PasswordHasher.hash("secret1");

        assertTrue(PasswordHasher.verify("secret1", stored));
    }

    @Test
    void verify_withWrongPassword_fails() {
        String stored = PasswordHasher.hash("secret1");

        assertFalse(PasswordHasher.verify("Secret1", stored));
        assertFalse(PasswordHasher.verify("", stored));
    }

    @Test
    void hash_usesRandomSalt_soTwoHashesDiffer() {
        String first = PasswordHasher.hash("secret1");
        String second = PasswordHasher.hash("secret1");

        assertNotEquals(first, second);
        assertTrue(PasswordHasher.verify("secret1", first));
        assertTrue(PasswordHasher.verify("secret1", second));
    }

    @Test
    void hash_doesNotContainThePlainPassword() {
        String stored = PasswordHasher.hash("secret1");

        assertFalse(stored.contains("secret1"));
    }

    @Test
    void hash_hasSaltAndHashParts() {
        String stored = PasswordHasher.hash("secret1");

        assertEquals(2, stored.split(":").length);
    }
}
