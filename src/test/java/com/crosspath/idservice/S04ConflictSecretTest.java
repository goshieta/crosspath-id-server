package com.crosspath.idservice;

import com.crosspath.idservice.api.dto.RegistrationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S04: 同一 secret・別 request_id → 409、その後 GET /me で既存 ID が取れる。
 */
class S04ConflictSecretTest extends BaseIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void testS04() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String secretB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UUID requestId1 = UUID.randomUUID();
        UUID requestId2 = UUID.randomUUID();

        RestClient client = createRestClient();

        // 1回目: 成功
        RegistrationResponse resp1 = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64)
                .body(Map.of("request_id", requestId1.toString()))
                .retrieve()
                .body(RegistrationResponse.class);
        assertNotNull(resp1);

        // 2回目: 同一 secret, 別 request_id → 409
        ResponseEntity<Void> resp2 = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64)
                .body(Map.of("request_id", requestId2.toString()))
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toBodilessEntity();
        assertEquals(409, resp2.getStatusCode().value());

        // GET /me で既存 ID が取れる
        RegistrationResponse me = client.get()
                .uri("/v1/registrations/me")
                .header("Authorization", "Bearer " + secretB64)
                .retrieve()
                .body(RegistrationResponse.class);
        assertNotNull(me);
        assertEquals(resp1.getUserId(), me.getUserId());
        assertEquals(resp1.getRequestId(), me.getRequestId());
    }

}