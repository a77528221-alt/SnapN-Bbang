package com.snapnbbang.common;

public class RateLimitExceededException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "요청이 너무 많아요. 잠시 후 다시 시도해 주세요";

    public RateLimitExceededException() {
        super(DEFAULT_MESSAGE);
    }

    public RateLimitExceededException(String message) {
        super(message);
    }
}

