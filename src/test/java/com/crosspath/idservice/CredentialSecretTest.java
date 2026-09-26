package com.crosspath.idservice;

import com.crosspath.idservice.domain.ApiException;
import com.crosspath.idservice.domain.CredentialSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CredentialSecretTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void validSecretDecodesTo32Bytes() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        assertEquals(43, secret.length());

        CredentialSecret cs = new CredentialSecret(secret);
        assertNotNull(cs);
    }

    @Test
    void sha256HashIs32Bytes() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        CredentialSecret cs = new CredentialSecret(secret);
        byte[] hash = cs.getHash();
        assertEquals(32, hash.length);
    }

    @Test
    void constantTimeEqualsWorks() {
        byte[] a = new byte[]{1, 2, 3, 4};
        byte[] b = new byte[]{1, 2, 3, 4};
        byte[] c = new byte[]{1, 2, 3, 5};
        assertTrue(CredentialSecret.constantTimeEquals(a, b));
        assertFalse(CredentialSecret.constantTimeEquals(a, c));
    }

    @Test
    void constantTimeEqualsDifferentLengthReturnsFalse() {
        byte[] a = new byte[]{1, 2, 3};
        byte[] b = new byte[]{1, 2, 3, 4};
        assertFalse(CredentialSecret.constantTimeEquals(a, b));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", // too short
            "short", // too short
            "!!!invalid!!!base64!!!chars!!!length!!!43!!", // 43 chars but invalid base64 (invalid chars)
            "AAAA", // too short
    })
    void rejectInvalidSecret(String invalidSecret) {
        assertThrows(ApiException.class, () -> new CredentialSecret(invalidSecret));
    }

    @Test
    void rejectPaddedSecret() {
        // 32 bytes = 43 chars without padding. With padding = 44 chars.
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String padded = Base64.getUrlEncoder().encodeToString(raw); // with padding
        assertEquals(44, padded.length());
        assertThrows(ApiException.class, () -> new CredentialSecret(padded));
    }

    @Test
    void rejectNullSecret() {
        assertThrows(ApiException.class, () -> new CredentialSecret(null));
    }

}