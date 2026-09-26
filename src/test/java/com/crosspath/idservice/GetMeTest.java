package com.crosspath.idservice;

import com.crosspath.idservice.api.dto.RegistrationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GET /me: 未登録 secret → 401。
 * 登録済み secret → 200 と正しい情報。
 */
class GetMeTest extends BaseIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void unregisteredSecretReturns401() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String secretB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);

        RestClient client = createRestClient();
        ResponseEntity<String> resp = client.get()
                .uri("/v1/registrations/me")
                .header("Authorization", "Bearer " + secretB64)
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toEntity(String.class);

        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    void registeredSecretReturnsCorrectInfo() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String secretB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UUID requestId = UUID.randomUUID();

        RestClient client = createRestClient();

        // 登録
        RegistrationResponse reg = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64)
                .body(Map.of("request_id", requestId.toString()))
                .retrieve()
                .body(RegistrationResponse.class);
        assertNotNull(reg);

        // GET /me
        RegistrationResponse me = client.get()
                .uri("/v1/registrations/me")
                .header("Authorization", "Bearer " + secretB64)
                .retrieve()
                .body(RegistrationResponse.class);
        assertNotNull(me);
        assertEquals(reg.getUserId(), me.getUserId());
        assertEquals(reg.getRequestId(), me.getRequestId());
        assertEquals(reg.getCreatedAt(), me.getCreatedAt());
    }

}