package com.crosspath.idservice.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RegistrationResponse {

    @JsonProperty("request_id")
    private String requestId;
    @JsonProperty("user_id")
    private int userId;
    @JsonProperty("created_at")
    private String createdAt;

    public RegistrationResponse() {
    }

    public RegistrationResponse(String requestId, int userId, String createdAt) {
        this.requestId = requestId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    @JsonProperty("request_id")
    public String getRequestId() {
        return requestId;
    }

    @JsonProperty("request_id")
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    @JsonProperty("user_id")
    public int getUserId() {
        return userId;
    }

    @JsonProperty("user_id")
    public void setUserId(int userId) {
        this.userId = userId;
    }

    @JsonProperty("created_at")
    public String getCreatedAt() {
        return createdAt;
    }

    @JsonProperty("created_at")
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

}