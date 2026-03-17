package com.vulnaudit.exception;

import lombok.Getter;

@Getter
public class UnauthorizedException extends RuntimeException {
    private final String code;

    public UnauthorizedException(String message) {
        super(message);
        this.code = "UNAUTHORIZED";
    }

    public UnauthorizedException(String code, String message) {
        super(message);
        this.code = code;
    }
}
