package com.crosspath.idservice.domain;

public enum ApiErrorCode {
    INVALID_REQUEST(400, false),
    INVALID_CREDENTIAL(401, false),
    NOT_FOUND(404, false),
    METHOD_NOT_ALLOWED(405, false),
    REGISTRATION_CONFLICT(409, false),
    REGISTRATION_RETIRED(410, false),
    RATE_LIMITED(429, true),
    INTERNAL_ERROR(500, true),
    REGISTRATION_BUSY(503, true),
    ID_SPACE_EXHAUSTED(503, false),
    SERVICE_UNAVAILABLE(503, true);

    private final int httpStatus;
    private final boolean retryable;

    ApiErrorCode(int httpStatus, boolean retryable) {
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }

}