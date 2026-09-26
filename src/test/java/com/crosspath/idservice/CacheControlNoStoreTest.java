package com.crosspath.idservice;

import com.crosspath.idservice.api.dto.RegistrationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全応答に Cache-Control: no-store が付くことの確認。
 */
class CacheControlNoStoreTest extends BaseIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void successResponseHasNoStore() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String secretB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UUID requestId = UUID.randomUUID();

        RestClient client = createRestClient();
        ResponseEntity<RegistrationResponse> resp = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64)
                .body(Map.of("request_id", requestId.toString()))
                .retrieve()
                .toEntity(RegistrationResponse.class);

        assertEquals("no-store", resp.getHeaders().getFirst("Cache-Control"));
    }

    @Test
    void errorResponseHasNoStore() {
        RestClient client = createRestClient();
        ResponseEntity<String> resp = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer invalid")
                .header("Content-Type", "application/json")
                .body("{\"request_id\":\"abc\"}")
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toEntity(String.class);

        assertEquals("no-store", resp.getHeaders().getFirst("Cache-Control"));
    }

    @Test
    void getMeHasNoStore() {
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

        assertEquals("no-store", resp.getHeaders().getFirst("Cache-Control"));
    }

}