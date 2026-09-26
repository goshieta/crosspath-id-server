package com.crosspath.idservice.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

/**
 * 43文字 base64url (no padding) = 32バイト secret の復号・検証・SHA-256ハッシュ化。
 */
public class CredentialSecret {

    private static final int EXPECTED_SECRET_BYTES = 32;
    private static final int EXPECTED_SECRET_STRING_LEN = 43; // base64url no-padding for 32 bytes

    private final byte[] rawSecret;
    private final byte[] hash;

    public CredentialSecret(String secret) {
        if (secret == null || secret.length() != EXPECTED_SECRET_STRING_LEN) {
            throw new ApiException(ApiErrorCode.INVALID_CREDENTIAL);
        }
        try {
            rawSecret = Base64.getUrlDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.INVALID_CREDENTIAL);
        }
        if (rawSecret.length != EXPECTED_SECRET_BYTES) {
            throw new ApiException(ApiErrorCode.INVALID_CREDENTIAL);
        }
        this.hash = sha256(rawSecret);
    }

    public byte[] getHash() {
        return hash;
    }

    /**
     * 定数時間比較。length mismatch の場合は false を返す（secret leaked 防止のため早期returnしない）。
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

}