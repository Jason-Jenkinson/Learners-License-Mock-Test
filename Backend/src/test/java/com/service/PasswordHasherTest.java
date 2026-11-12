package com.mocktest.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PasswordHasher.
 * Verifies that passwords are hashed with a random salt, that the stored
 * value never exposes the plain password, and that verification accepts only
 * the original password.
 */
class PasswordHasherTest {

    /**
     * Checks that a password hashed with hash is accepted by
     * verify when the same password is supplied.
     */
    @Test
    void hash_thenVerify_withSamePassword_succeeds() {
        String stored = PasswordHasher.hash("secret1");

        assertTrue(PasswordHasher.verify("secret1", stored));
    }

    /**
     * Checks that verify rejects incorrect input: a password that
     * differs only in letter case, and an empty string.
     */
    @Test
    void verify_withWrongPassword_fails() {
        String stored = PasswordHasher.hash("secret1");

        assertFalse(PasswordHasher.verify("Secret1", stored));
        assertFalse(PasswordHasher.verify("", stored));
    }

    /**
     * Checks that hash uses a random salt, so hashing the same
     * password twice gives different stored values, while both values still
     * verify against that password.
     */
    @Test
    void hash_usesRandomSalt_soTwoHashesDiffer() {
        String first = PasswordHasher.hash("secret1");
        String second = PasswordHasher.hash("secret1");

        assertNotEquals(first, second);
        assertTrue(PasswordHasher.verify("secret1", first));
        assertTrue(PasswordHasher.verify("secret1", second));
    }

    /**
     * Checks that the stored hash does not contain the plain-text password,
     * so the password cannot be read directly from the stored value.
     */
    @Test
    void hash_doesNotContainThePlainPassword() {
        String stored = PasswordHasher.hash("secret1");

        assertFalse(stored.contains("secret1"));
    }

    /**
     * Checks the format of the stored value: it must consist of exactly two
     * parts, the salt and the hash, separated by a colon.
     */
    @Test
    void hash_hasSaltAndHashParts() {
        String stored = PasswordHasher.hash("secret1");

        assertEquals(2, stored.split(":").length);
    }
}