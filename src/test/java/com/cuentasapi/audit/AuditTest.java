package com.cuentasapi.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuentasapi.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuditTest extends IntegrationTest {

    private Map<String, Object> audit(String token, String query) {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/api/v1/audit" + query, token, null, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> content(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("content");
    }

    @Test
    void registering_and_logging_in_are_audited() {
        clientToken("ana@x.com");
        rest.postForEntity("/api/v1/auth/login", Map.of("email", "ana@x.com", "password", "wrong-password"), Map.class);

        List<Map<String, Object>> entries = content(audit(operatorToken(), ""));

        assertThat(entries).extracting(e -> e.get("action") + ":" + e.get("outcome"))
                .contains("REGISTER:OK", "LOGIN:OK", "LOGIN:FAILED");
        Map<String, Object> failed = entries.stream()
                .filter(e -> "FAILED".equals(e.get("outcome"))).findFirst().orElseThrow();
        assertThat(failed.get("actorId")).isNull();
    }

    @Test
    void operator_can_filter_by_action_and_actor() {
        clientToken("ana@x.com");
        clientToken("ben@x.com");
        String operator = operatorToken();
        long anaId = jdbc.queryForObject("select id from users where email = 'ana@x.com'", Long.class);

        List<Map<String, Object>> registers = content(audit(operator, "?action=REGISTER"));
        List<Map<String, Object>> byActor = content(audit(operator, "?actor=" + anaId));

        assertThat(registers).hasSize(2).allMatch(e -> "REGISTER".equals(e.get("action")));
        assertThat(byActor).isNotEmpty().allMatch(e -> Long.valueOf(anaId).equals(((Number) e.get("actorId")).longValue()));
    }

    @Test
    void entries_are_newest_first() {
        clientToken("ana@x.com");
        List<Map<String, Object>> entries = content(audit(operatorToken(), ""));

        assertThat(entries.get(0).get("action")).isEqualTo("LOGIN");
        assertThat(entries.get(entries.size() - 1).get("action")).isEqualTo("REGISTER");
    }

    @Test
    void size_above_100_is_capped_to_100() {
        Map<String, Object> page = audit(operatorToken(), "?size=100000");

        assertThat(page.get("size")).isEqualTo(100);
    }

    @Test
    void cliente_gets_403() {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/api/v1/audit", clientToken("c@x.com"), null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
