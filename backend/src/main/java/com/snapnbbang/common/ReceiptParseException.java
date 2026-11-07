package com.snapnbbang.common;

public class ReceiptParseException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "영수증을 읽지 못했어요";

    public ReceiptParseException() {
        super(DEFAULT_MESSAGE);
    }

    public ReceiptParseException(String message) {
        super(message);
    }

    public ReceiptParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

