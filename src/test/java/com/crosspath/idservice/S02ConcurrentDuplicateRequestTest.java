package com.crosspath.idservice;

import com.crosspath.idservice.api.dto.RegistrationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S02: 同一 request_id・secret を 100 回並行送信 → registrations 1 行、返る user_id が全一致。
 */
class S02ConcurrentDuplicateRequestTest extends BaseIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testS02() throws Exception {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String secretB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UUID requestId = UUID.randomUUID();

        int n = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Callable<RegistrationResponse>> tasks = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            tasks.add(() -> {
                RestClient client = createRestClient();
                return client.post()
                        .uri("/v1/registrations")
                        .header("Authorization", "Bearer " + secretB64)
                        .body(Map.of("request_id", requestId.toString()))
                        .retrieve()
                        .body(RegistrationResponse.class);
            });
        }

        List<Future<RegistrationResponse>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
        executor.shutdown();

        Set<Integer> userIds = new HashSet<>();
        for (Future<RegistrationResponse> f : futures) {
            RegistrationResponse resp = f.get();
            userIds.add(resp.getUserId());
        }

        assertEquals(1, userIds.size(), "All responses must return the same user_id");
        // registrations テーブルが 1 行だけであることを確認
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registrations WHERE request_id::text = ?",
                Integer.class, requestId.toString());
        assertEquals(1, count, "registrations must have exactly 1 row");

        // 別 request_id + 同一 secret → 409 で重複登録を拒否
        UUID requestId2 = UUID.randomUUID();
        ResponseEntity<Void> conflictResp = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build().post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64)
                .body(Map.of("request_id", requestId2.toString()))
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toBodilessEntity();
        assertEquals(409, conflictResp.getStatusCode().value());
    }

}