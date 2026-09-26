package com.crosspath.idservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * registrations テーブル操作用リポジトリ。
 */
@Repository
public class RegistrationRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<RegistrationRow> ROW_MAPPER = (rs, rowNum) -> {
        int userId = rs.getInt("user_id");
        UUID requestId = rs.getObject("request_id", UUID.class);
        byte[] credentialHash = rs.getBytes("credential_hash");
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        String state = rs.getString("state");
        return new RegistrationRow(userId, requestId, credentialHash, createdAt, state);
    };

    public RegistrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * request_id で検索。
     */
    public Optional<RegistrationRow> findByRequestId(UUID requestId) {
        var list = jdbcTemplate.query(
                "SELECT user_id, request_id, credential_hash, created_at, state FROM registrations WHERE request_id = ?",
                ROW_MAPPER,
                requestId
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * credential_hash で検索。
     */
    public Optional<RegistrationRow> findByCredentialHash(byte[] credentialHash) {
        var list = jdbcTemplate.query(
                "SELECT user_id, request_id, credential_hash, created_at, state FROM registrations WHERE credential_hash = ?",
                ROW_MAPPER,
                credentialHash
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 新規登録行を挿入し、DB が生成した created_at を返す。
     */
    public OffsetDateTime insert(int userId, UUID requestId, byte[] credentialHash) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO registrations(user_id, request_id, credential_hash, created_at, state) VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'ACTIVE') RETURNING created_at",
                OffsetDateTime.class,
                userId,
                requestId,
                credentialHash
        );
    }

}