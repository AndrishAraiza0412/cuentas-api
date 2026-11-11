package com.cuentasapi.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuentasapi.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AccountTest extends IntegrationTest {

    private long open(String token) {
        ResponseEntity<Map> response = call(HttpMethod.POST, "/api/v1/accounts", token, null, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> content(ResponseEntity<Map> response) {
        return (List<Map<String, Object>>) response.getBody().get("content");
    }

    @Test
    void cliente_opens_account_with_zero_balance() {
        ResponseEntity<Map> response = call(HttpMethod.POST, "/api/v1/accounts", clientToken("ana@x.com"), null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("balance")).isEqualTo(0);
        assertThat(response.getBody().get("status")).isEqualTo("ACTIVE");
        assertThat((String) response.getBody().get("number")).matches("\\d{12}");
    }

    @Test
    void cliente_lists_only_own_accounts() {
        String ana = clientToken("ana@x.com");
        String ben = clientToken("ben@x.com");
        open(ana);
        open(ana);
        open(ben);

        assertThat(content(call(HttpMethod.GET, "/api/v1/accounts", ana, null, Map.class))).hasSize(2);
        assertThat(content(call(HttpMethod.GET, "/api/v1/accounts", ben, null, Map.class))).hasSize(1);
    }

    @Test
    void other_customers_account_is_404_not_403() {
        String ana = clientToken("ana@x.com");
        String ben = clientToken("ben@x.com");
        long anaAccount = open(ana);

        assertThat(call(HttpMethod.GET, "/api/v1/accounts/" + anaAccount, ben, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, "/api/v1/accounts/" + anaAccount + "/entries", ben, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void operator_sees_every_account() {
        long first = open(clientToken("ana@x.com"));
        open(clientToken("ben@x.com"));
        String operator = operatorToken();

        assertThat(content(call(HttpMethod.GET, "/api/v1/accounts", operator, null, Map.class))).hasSize(2);
        assertThat(call(HttpMethod.GET, "/api/v1/accounts/" + first, operator, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void nonexistent_account_is_404() {
        assertThat(call(HttpMethod.GET, "/api/v1/accounts/999999", clientToken("ana@x.com"), null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void operator_cannot_open_an_account_403() {
        assertThat(call(HttpMethod.POST, "/api/v1/accounts", operatorToken(), null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void entries_are_paged_newest_first_and_capped_at_100() {
        String ana = clientToken("ana@x.com");
        long account = open(ana);
        jdbc.update("insert into ledger_entries (account_id, type, amount, balance_after, created_at) "
                + "select ?, 'CREDIT', 1, g, now() from generate_series(1, 120) g", account);

        ResponseEntity<Map> response = call(HttpMethod.GET,
                "/api/v1/accounts/" + account + "/entries?size=100000", ana, null, Map.class);

        assertThat(response.getBody().get("size")).isEqualTo(100);
        assertThat(response.getBody().get("totalElements")).isEqualTo(120);
        List<Map<String, Object>> entries = content(response);
        assertThat(entries).hasSize(100);
        assertThat(((Number) entries.get(0).get("balanceAfter")).longValue()).isEqualTo(120);
    }
}
