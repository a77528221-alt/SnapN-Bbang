package com.snapnbbang.common;

public class ConflictException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "이미 선택된 이름이에요";

    public ConflictException() {
        super(DEFAULT_MESSAGE);
    }

    public ConflictException(String message) {
        super(message);
    }
}
