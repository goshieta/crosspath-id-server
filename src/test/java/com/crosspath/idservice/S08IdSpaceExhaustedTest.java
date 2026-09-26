package com.crosspath.idservice;

import com.crosspath.idservice.api.dto.RegistrationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S08: next_id を 16777215 に更新した状態 → 1 件だけ 16777215 が発行され、
 * 次は 503 ID_SPACE_EXHAUSTED、既存要求の再送は 200。
 */
class S08IdSpaceExhaustedTest extends BaseIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testS08() {
        // id_allocator の next_id を 16777215 に直接更新
        jdbcTemplate.update("UPDATE id_allocator SET next_id = 16777215 WHERE singleton = 1");

        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String secretB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UUID requestId = UUID.randomUUID();

        RestClient client = createRestClient();

        // 1回目: 16777215 が発行される
        ResponseEntity<RegistrationResponse> resp1 = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64)
                .body(Map.of("request_id", requestId.toString()))
                .retrieve()
                .toEntity(RegistrationResponse.class);
        assertEquals(201, resp1.getStatusCode().value());
        assertEquals(16777215, resp1.getBody().getUserId());

        // 2回目: 別 secret で next_id が 16777216 になり、枯渇
        byte[] secret2 = new byte[32];
        RANDOM.nextBytes(secret2);
        String secretB64_2 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret2);
        UUID requestId2 = UUID.randomUUID();

        ResponseEntity<Map> resp2 = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64_2)
                .body(Map.of("request_id", requestId2.toString()))
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toEntity(Map.class);
        assertEquals(503, resp2.getStatusCode().value());
        assertEquals("ID_SPACE_EXHAUSTED",
                ((Map<String, Map<String, Object>>) resp2.getBody()).get("error").get("code"));

        // 既存要求の再送は 200
        ResponseEntity<RegistrationResponse> resp3 = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64)
                .body(Map.of("request_id", requestId.toString()))
                .retrieve()
                .toEntity(RegistrationResponse.class);
        assertEquals(200, resp3.getStatusCode().value());
        assertEquals(16777215, resp3.getBody().getUserId());
    }

}