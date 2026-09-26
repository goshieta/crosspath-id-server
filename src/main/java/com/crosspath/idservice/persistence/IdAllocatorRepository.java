package com.crosspath.idservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;

/**
 * id_allocator テーブル操作用リポジトリ。
 * 単一行 (singleton=1) に対して SELECT FOR UPDATE / UPDATE を行う。
 */
@Repository
public class IdAllocatorRepository {

    private final JdbcTemplate jdbcTemplate;

    public IdAllocatorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int selectNextIdForUpdate() {
        Integer nextId = jdbcTemplate.queryForObject(
                "SELECT next_id FROM id_allocator WHERE singleton = 1 FOR UPDATE",
                Integer.class
        );
        if (nextId == null) {
            throw new IllegalStateException("id_allocator row missing (singleton=1)");
        }
        return nextId;
    }

    public void incrementNextId() {
        jdbcTemplate.update(
                "UPDATE id_allocator SET next_id = next_id + 1 WHERE singleton = 1"
        );
    }

}