package com.crosspath.idservice.domain;

import java.util.UUID;

/**
 * UUID v4 バリデーター。
 * version nibble=4, variant=10xx (UUID v4 の要件) を検証する。
 */
public class RequestIdValidator {

    private RequestIdValidator() {
    }

    /**
     * requestId が UUID v4 として有効か検証する。
     *
     * @param requestId リクエストID文字列
     * @return パース済みUUID
     * @throws ApiException 無効な場合 INVALID_REQUEST
     */
    public static UUID validate(String requestId) {
        if (requestId == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(requestId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        // UUID v4 検証: version = 4, variant = 2 (IETF)
        if (uuid.version() != 4) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        // UUID.variant() returns -1 (non-IETF), 0 (NCS), 2 (IETF), 6 (Microsoft), 7 (future)
        if (uuid.variant() != 2) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        return uuid;
    }

}