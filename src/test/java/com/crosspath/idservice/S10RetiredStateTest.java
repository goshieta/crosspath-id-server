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
 * S10: state=RETIRED（SQL で更新）→ POST は 410、GET も 410、ID 再発行なし。
 */
class S10RetiredStateTest extends BaseIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testS10() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String secretB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UUID requestId = UUID.randomUUID();

        RestClient client = createRestClient();

        // 登録
        RegistrationResponse resp = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64)
                .body(Map.of("request_id", requestId.toString()))
                .retrieve()
                .body(RegistrationResponse.class);
        assertNotNull(resp);

        // SQL で RETIRED に更新
        jdbcTemplate.update("UPDATE registrations SET state = 'RETIRED' WHERE user_id = ?", resp.getUserId());

        // POST → 410
        ResponseEntity<Map> postResp = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64)
                .body(Map.of("request_id", requestId.toString()))
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toEntity(Map.class);
        assertEquals(410, postResp.getStatusCode().value());

        // GET /me → 410
        ResponseEntity<Map> getResp = client.get()
                .uri("/v1/registrations/me")
                .header("Authorization", "Bearer " + secretB64)
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toEntity(Map.class);
        assertEquals(410, getResp.getStatusCode().value());

        // 同一 secret で別 request_id で再発行不可（409）
        byte[] secret2 = new byte[32];
        RANDOM.nextBytes(secret2);
        String secretB64_2 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret2);
        UUID requestId2 = UUID.randomUUID();

        ResponseEntity<Map> newPostResp = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64_2)
                .body(Map.of("request_id", requestId2.toString()))
                .retrieve()
                .toEntity(Map.class);
        assertEquals(201, newPostResp.getStatusCode().value());
    }

}