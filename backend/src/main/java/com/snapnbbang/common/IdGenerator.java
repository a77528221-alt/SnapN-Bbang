package com.snapnbbang.common;

import java.security.SecureRandom;

public final class IdGenerator {

    private static final char[] URL_SAFE_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_-".toCharArray();
    private static final int DEFAULT_ID_LENGTH = 21;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    public static String generate() {
        byte[] randomBytes = new byte[DEFAULT_ID_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        char[] id = new char[DEFAULT_ID_LENGTH];
        for (int i = 0; i < DEFAULT_ID_LENGTH; i++) {
            id[i] = URL_SAFE_ALPHABET[randomBytes[i] & 63];
        }
        return new String(id);
    }
}

