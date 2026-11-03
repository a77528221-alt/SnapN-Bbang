package com.snapnbbang.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("찾을 수 없어요");
    }
}

