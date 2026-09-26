package com.crosspath.idservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * レート制限が正しく動作することを確認するテスト。
 * app.rate-limit.enabled=true, per-IP 1 req/min, burst=1 で短時間に連続 POST → 429 + Retry-After.
 *
 * 注意: このテストは専用の Spring コンテキストを持つ（properties が異なるため）。
 * コンテナは BaseIntegrationTest のシングルトン POSTGRES を共有する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.rate-limit.enabled=true",
                "app.rate-limit.per-ip-per-minute=1",
                "app.rate-limit.per-ip-burst=1"
        })
class RateLimitTest extends BaseIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void rateLimitReturns429AndRetryAfter() {
        RestClient client = createRestClient();

        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        UUID requestId = UUID.randomUUID();

        // 1回目: 成功 (per-IP bucket 初期 1 トークン)
        ResponseEntity<String> resp1 = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secret)
                .body(Map.of("request_id", requestId.toString()))
                .retrieve()
                .toEntity(String.class);
        assertEquals(201, resp1.getStatusCode().value());

        // 2回目: 同一 IP → レート制限 (429)
        // exchange() で 429 を例外にせず取得
        String secret2 = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        UUID requestId2 = UUID.randomUUID();

        ResponseEntity<String> resp2 = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secret2)
                .body(Map.of("request_id", requestId2.toString()))
                .exchange((req, res) -> {
                    String body = new String(res.getBody().readAllBytes());
                    return ResponseEntity.status(res.getStatusCode())
                            .headers(res.getHeaders())
                            .body(body);
                });

        assertEquals(429, resp2.getStatusCode().value());
        assertNotNull(resp2.getHeaders().getFirst("Retry-After"));
    }

}