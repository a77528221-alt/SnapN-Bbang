package com.snapnbbang.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenUtil {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TokenUtil() {
    }

    public static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("Token must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static boolean matchesHash(String expectedHash, String actualHash) {
        if (expectedHash == null || actualHash == null) {
            return false;
        }
        byte[] expectedBytes = expectedHash.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actualHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    public static boolean verifyToken(String rawToken, String storedHash) {
        if (rawToken == null || storedHash == null) {
            return false;
        }
        String computedHash = sha256Hex(rawToken);
        return matchesHash(storedHash, computedHash);
    }
}

