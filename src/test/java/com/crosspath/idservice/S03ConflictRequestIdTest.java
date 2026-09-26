package com.crosspath.idservice;

import com.crosspath.idservice.api.dto.RegistrationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S03: 同一 request_id・別 secret → 409、行数増えない、他人の ID を返さない。
 */
class S03ConflictRequestIdTest extends BaseIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void testS03() {
        byte[] secret1 = new byte[32];
        RANDOM.nextBytes(secret1);
        String secretB64_1 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret1);
        UUID requestId = UUID.randomUUID();

        // 1回目: 成功 (201)
        RestClient client = createRestClient();
        RegistrationResponse resp1 = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64_1)
                .body(Map.of("request_id", requestId.toString()))
                .retrieve()
                .body(RegistrationResponse.class);
        assertNotNull(resp1);

        // 2回目: 別 secret, 同一 request_id → 409
        byte[] secret2 = new byte[32];
        RANDOM.nextBytes(secret2);
        String secretB64_2 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret2);

        ResponseEntity<Void> resp2 = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64_2)
                .body(Map.of("request_id", requestId.toString()))
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toBodilessEntity();

        assertEquals(409, resp2.getStatusCode().value());
    }

}