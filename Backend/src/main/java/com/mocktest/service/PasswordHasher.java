package com.mocktest.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordHasher {
    private static byte[] pbkdf2(String pw, byte[] salt) {
        try {
            var spec = new PBEKeySpec(pw.toCharArray(), salt, 120_000, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hash(String pw) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(pbkdf2(pw, salt));
    }

    public static boolean verify(String pw, String stored) {
        String[] p = stored.split(":");
        byte[] salt = Base64.getDecoder().decode(p[0]);
        byte[] expected = Base64.getDecoder().decode(p[1]);
        return MessageDigest.isEqual(expected, pbkdf2(pw, salt));
    }
}