package com.crosspath.idservice;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /actuator/health が 200 を返すことの確認。
 */
class HealthEndpointTest extends BaseIntegrationTest {

    @Test
    void healthEndpointReturns200() {
        RestClient client = createRestClient();
        ResponseEntity<Map> resp = client.get()
                .uri("/actuator/health")
                .retrieve()
                .toEntity(Map.class);

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals("UP", resp.getBody().get("status"));
    }

}