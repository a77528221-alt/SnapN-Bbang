package com.snapnbbang.common;

public class ServerConfigurationException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "서버 설정 오류";

    public ServerConfigurationException() {
        super(DEFAULT_MESSAGE);
    }
}

