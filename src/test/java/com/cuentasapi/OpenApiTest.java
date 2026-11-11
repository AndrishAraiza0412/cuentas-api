package com.cuentasapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OpenApiTest extends IntegrationTest {

    @SuppressWarnings("unchecked")
    @Test
    void api_docs_list_every_endpoint_and_the_bearer_scheme() {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/v3/api-docs", null, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> paths = (Map<String, Object>) response.getBody().get("paths");
        assertThat(paths).containsKeys("/api/v1/transfers", "/api/v1/accounts/{id}/deposits", "/api/v1/audit",
                "/api/v1/auth/login", "/api/v1/accounts/{id}/entries");
        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        assertThat((Map<String, Object>) components.get("securitySchemes")).containsKey("bearerAuth");
        Map<String, Object> transfers = (Map<String, Object>) ((Map<String, Object>) paths.get("/api/v1/transfers")).get("post");
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) transfers.get("parameters");
        assertThat(parameters).extracting(p -> p.get("name")).contains("Idempotency-Key");
    }

    @Test
    void health_is_open_and_up() {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/actuator/health", null, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }
}
