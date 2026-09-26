package com.crosspath.idservice;

import com.crosspath.idservice.api.dto.RegistrationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S01: 異なる 100 要求を並行送信 → user_id 100 件が一意、1〜100 が漏れなく発行。
 */
class S01ConcurrentRegistrationsTest extends BaseIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void testS01() throws Exception {
        int n = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Callable<RegistrationResponse>> tasks = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            tasks.add(() -> {
                RestClient client = createRestClient();
                byte[] secret = new byte[32];
                RANDOM.nextBytes(secret);
                String secretB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
                UUID requestId = UUID.randomUUID();

                RegistrationResponse resp = client.post()
                        .uri("/v1/registrations")
                        .header("Authorization", "Bearer " + secretB64)
                        .body(Map.of("request_id", requestId.toString()))
                        .retrieve()
                        .body(RegistrationResponse.class);
                return resp;
            });
        }

        List<Future<RegistrationResponse>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
        executor.shutdown();

        Set<Integer> userIds = new HashSet<>();
        for (Future<RegistrationResponse> f : futures) {
            RegistrationResponse resp = f.get();
            userIds.add(resp.getUserId());
        }

        assertEquals(100, userIds.size(), "All 100 user IDs must be unique");
        // 1..100 が漏れなく発行
        for (int i = 1; i <= 100; i++) {
            assertTrue(userIds.contains(i), "user_id " + i + " should be present");
        }
    }

}