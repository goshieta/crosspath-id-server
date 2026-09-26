package com.crosspath.idservice.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RegistrationRequest {

    @JsonProperty("request_id")
    private String requestId;

    public String getRequestId() {
        return requestId;
    }

    @JsonProperty("request_id")
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

}