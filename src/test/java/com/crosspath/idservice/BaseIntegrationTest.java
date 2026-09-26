package com.crosspath.idservice;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("crosspath")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 各テストメソッド実行前にデータベースを初期状態にリセットする。
     * テスト間の干渉（ID枯渇・RETIRED等）を防止する。
     */
    @BeforeEach
    void resetDatabaseForTest() {
        jdbcTemplate.update("TRUNCATE TABLE registrations");
        jdbcTemplate.update("UPDATE id_allocator SET next_id = 1 WHERE singleton = 1");
    }

    @LocalServerPort
    protected int port;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    protected RestClient createRestClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

}