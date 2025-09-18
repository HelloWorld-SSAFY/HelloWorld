package com.example.helloworld.userserver.exception;

import com.example.helloworld.userserver.exception.code.AuthErrorCode;

public class HelloWordException extends RuntimeException {
    private final AuthErrorCode code;

    public HelloWordException(AuthErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public AuthErrorCode getCode() {
        return code;
    }
}
