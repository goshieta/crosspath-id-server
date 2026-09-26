package com.crosspath.idservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S09: SQL 直接 INSERT で user_id=0 / -1 / 16777216 → CHECK 制約違反。next_id の範囲外も CHECK 違反。
 */
class S09CheckConstraintTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testInsertUserIdZeroFails() {
        UUID requestId = UUID.randomUUID();
        byte[] hash = new byte[32];
        assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO registrations(user_id, request_id, credential_hash) VALUES (?, ?, ?)",
                        0, requestId.toString(), hash));
    }

    @Test
    void testInsertUserIdNegativeFails() {
        UUID requestId = UUID.randomUUID();
        byte[] hash = new byte[32];
        assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO registrations(user_id, request_id, credential_hash) VALUES (?, ?, ?)",
                        -1, requestId.toString(), hash));
    }

    @Test
    void testInsertUserId16777216Fails() {
        UUID requestId = UUID.randomUUID();
        byte[] hash = new byte[32];
        assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO registrations(user_id, request_id, credential_hash) VALUES (?, ?, ?)",
                        16777216, requestId.toString(), hash));
    }

    @Test
    void testInsertNextIdZeroFails() {
        assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "UPDATE id_allocator SET next_id = 0 WHERE singleton = 1"));
    }

    @Test
    void testInsertNextIdNegativeFails() {
        assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "UPDATE id_allocator SET next_id = -1 WHERE singleton = 1"));
    }

    @Test
    void testInsertNextIdTooLargeFails() {
        assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "UPDATE id_allocator SET next_id = 16777217 WHERE singleton = 1"));
    }

}