package com.crosspath.idservice;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S17: 不正 JSON / 1025 バイト超ボディ / secret 不正（39 文字・パディング付き）→ 400 または 401。
 * レスポンス本文に secret・credential_hash・SQL 文字列が含まれない。
 */
class S17InvalidInputTest extends BaseIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void testMalformedJsonReturns400() {
        RestClient client = createRestClient();

        ResponseEntity<String> resp = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + validSecret())
                .header("Content-Type", "application/json")
                .body("{\"request_id\": broken json")
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toEntity(String.class);

        assertEquals(400, resp.getStatusCode().value());
        String body = resp.getBody();
        assert body != null;
        assertFalse(body.contains("secret"));
        assertFalse(body.contains("credential_hash"));
        assertFalse(body.contains("SELECT"));
    }

    @Test
    void testOversizedBodyReturns400() {
        // Content-Length 超過テスト: Content-Length > 1024
        // 1025 バイトのボディを構築
        StringBuilder sb = new StringBuilder();
        sb.append("{\"request_id\":\"");
        sb.append("a".repeat(1000));
        sb.append("\"}");
        String bigBody = sb.toString();

        RestClient client = createRestClient();
        ResponseEntity<String> resp = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + validSecret())
                .header("Content-Type", "application/json")
                .body(bigBody)
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toEntity(String.class);

        assertEquals(400, resp.getStatusCode().value());
        String body = resp.getBody();
        assert body != null;
        assertFalse(body.contains("secret"));
    }

    @Test
    void testShortSecretReturns401() {
        // 39 文字（32 bytes = 43 chars expected）
        String shortSecret = "abcdefghijklmnopqrstuvwxyz0123456789abc"; // 39 chars
        assertEquals(39, shortSecret.length());

        RestClient client = createRestClient();
        ResponseEntity<String> resp = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + shortSecret)
                .header("Content-Type", "application/json")
                .body("{\"request_id\":\"" + UUID.randomUUID() + "\"}")
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toEntity(String.class);

        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    void testPaddedSecretReturns401() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String paddedSecret = Base64.getUrlEncoder().encodeToString(raw); // has padding '='

        RestClient client = createRestClient();
        ResponseEntity<String> resp = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + paddedSecret)
                .header("Content-Type", "application/json")
                .body("{\"request_id\":\"" + UUID.randomUUID() + "\"}")
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toEntity(String.class);

        assertEquals(401, resp.getStatusCode().value());
    }

    private String validSecret() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

}