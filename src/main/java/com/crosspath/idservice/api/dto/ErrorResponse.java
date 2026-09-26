package com.crosspath.idservice.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorResponse {

    private ErrorDetail error;

    public ErrorResponse() {
    }

    public ErrorResponse(String code, boolean retryable) {
        this.error = new ErrorDetail(code, retryable);
    }

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    public static class ErrorDetail {
        private String code;
        private boolean retryable;

        public ErrorDetail() {
        }

        public ErrorDetail(String code, boolean retryable) {
            this.code = code;
            this.retryable = retryable;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public void setRetryable(boolean retryable) {
            this.retryable = retryable;
        }
    }

}