package com.crosspath.idservice.domain;

public class ApiException extends RuntimeException {

    private final ApiErrorCode errorCode;
    private final String message;
    private final Integer retryAfterSeconds;

    public ApiException(ApiErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public ApiException(ApiErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ApiException(ApiErrorCode errorCode, String message, Integer retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

}