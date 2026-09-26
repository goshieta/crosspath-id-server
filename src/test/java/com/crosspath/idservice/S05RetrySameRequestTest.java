package com.crosspath.idservice;

import com.crosspath.idservice.api.dto.RegistrationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S05: 成功後の同一 POST 再送 → 200 かつ同じボディ（request_id/user_id/created_at）。
 */
class S05RetrySameRequestTest extends BaseIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void testS05() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String secretB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UUID requestId = UUID.randomUUID();

        RestClient client = createRestClient();

        // 1回目: 201
        ResponseEntity<RegistrationResponse> resp1 = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64)
                .body(Map.of("request_id", requestId.toString()))
                .retrieve()
                .toEntity(RegistrationResponse.class);
        assertEquals(201, resp1.getStatusCode().value());
        RegistrationResponse body1 = resp1.getBody();
        assertNotNull(body1);

        // 2回目: 200
        ResponseEntity<RegistrationResponse> resp2 = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64)
                .body(Map.of("request_id", requestId.toString()))
                .retrieve()
                .toEntity(RegistrationResponse.class);
        assertEquals(200, resp2.getStatusCode().value());
        RegistrationResponse body2 = resp2.getBody();
        assertNotNull(body2);

        // 同一ボディ
        assertEquals(body1.getRequestId(), body2.getRequestId());
        assertEquals(body1.getUserId(), body2.getUserId());
        assertEquals(body1.getCreatedAt(), body2.getCreatedAt());
    }

}