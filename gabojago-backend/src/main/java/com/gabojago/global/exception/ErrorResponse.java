package com.gabojago.global.exception;

import lombok.Getter;

@Getter
public class ErrorResponse {

    private final int statusCode;
    private final String error;
    private final String code;
    private final String message;
    private final String detail;

    public ErrorResponse(ErrorCode errorCode, String detail) {
        this.statusCode = errorCode.getHttpStatus().value();
        this.error = errorCode.getHttpStatus().name();
        this.code = errorCode.name();
        this.message = errorCode.getMessage();
        this.detail = detail;
    }
}
