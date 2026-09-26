package com.crosspath.idservice.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public class RegistrationResult {

    private final UUID requestId;
    private final int userId;
    private final OffsetDateTime createdAt;
    private final boolean isNewRegistration;

    public RegistrationResult(UUID requestId, int userId, OffsetDateTime createdAt, boolean isNewRegistration) {
        this.requestId = requestId;
        this.userId = userId;
        this.createdAt = createdAt;
        this.isNewRegistration = isNewRegistration;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public int getUserId() {
        return userId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isNewRegistration() {
        return isNewRegistration;
    }

}