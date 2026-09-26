package com.crosspath.idservice.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * registrations テーブルの行を表すレコード。
 */
public class RegistrationRow {

    private final int userId;
    private final UUID requestId;
    private final byte[] credentialHash;
    private final OffsetDateTime createdAt;
    private final String state;

    public RegistrationRow(int userId, UUID requestId, byte[] credentialHash, OffsetDateTime createdAt, String state) {
        this.userId = userId;
        this.requestId = requestId;
        this.credentialHash = credentialHash;
        this.createdAt = createdAt;
        this.state = state;
    }

    public int getUserId() {
        return userId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public byte[] getCredentialHash() {
        return credentialHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getState() {
        return state;
    }

}