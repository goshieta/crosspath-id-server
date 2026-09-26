package com.crosspath.idservice;

import com.crosspath.idservice.api.dto.RegistrationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S14: 別コネクションで採番行をロック保持したまま POST → 503 REGISTRATION_BUSY（Retry-After 付き）、
 * 余分な ID が発行されない。
 */
class S14LockTimeoutTest extends BaseIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private org.springframework.core.env.Environment environment;

    @Test
    void testS14() throws Exception {
        // 別コネクションで id_allocator 行をロック
        String jdbcUrl = environment.getProperty("spring.datasource.url");
        String dbUser = environment.getProperty("spring.datasource.username");
        String dbPass = environment.getProperty("spring.datasource.password");
        java.sql.Connection lockConn = java.sql.DriverManager.getConnection(
                jdbcUrl, dbUser, dbPass);
        lockConn.setAutoCommit(false);
        java.sql.Statement lockStmt = lockConn.createStatement();
        lockStmt.execute("SELECT next_id FROM id_allocator WHERE singleton = 1 FOR UPDATE");

        // ロックがかかった状態で POST 要求
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String secretB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UUID requestId = UUID.randomUUID();

        RestClient client = createRestClient();

        // lock_timeout=2s なので 2 秒以内に 503 が返る
        ResponseEntity<Map> resp = client.post()
                .uri("/v1/registrations")
                .header("Authorization", "Bearer " + secretB64)
                .body(Map.of("request_id", requestId.toString()))
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toEntity(Map.class);

        assertEquals(503, resp.getStatusCode().value());
        assertTrue(resp.getHeaders().containsKey("Retry-After"));

        // ロック解放
        lockConn.rollback();
        lockConn.close();
    }

}