package com.cuentasapi.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuentasapi.IntegrationTest;
import com.cuentasapi.common.Money;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OperatorActionsTest extends IntegrationTest {

    private long open(String token) {
        return ((Number) call(HttpMethod.POST, "/api/v1/accounts", token, null, Map.class).getBody().get("id")).longValue();
    }

    private ResponseEntity<Map> deposit(String token, long account, Object amount) {
        return call(HttpMethod.POST, "/api/v1/accounts/" + account + "/deposits", token, Map.of("amount", amount), Map.class);
    }

    @Test
    void deposit_credits_balance_and_writes_a_credit_entry() {
        long account = open(clientToken("ana@x.com"));

        ResponseEntity<Map> response = deposit(operatorToken(), account, 5000);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("balance")).isEqualTo(5000);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select type, amount, balance_after, transfer_id from ledger_entries where account_id = ?", account);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("type", "CREDIT").containsEntry("amount", 5000L)
                .containsEntry("balance_after", 5000L).containsEntry("transfer_id", null);
    }

    @Test
    void deposit_to_frozen_account_is_409() {
        long account = open(clientToken("ana@x.com"));
        String operator = operatorToken();
        call(HttpMethod.POST, "/api/v1/accounts/" + account + "/freeze", operator, null, Map.class);

        assertThat(deposit(operator, account, 100).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("select balance from accounts where id = ?", Long.class, account)).isZero();
    }

    @Test
    void deposit_that_is_not_a_whole_number_of_centavos_is_400() {
        long account = open(clientToken("ana@x.com"));

        assertThat(deposit(operatorToken(), account, 1.5).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deposit_above_max_zero_or_negative_is_400() {
        long account = open(clientToken("ana@x.com"));
        String operator = operatorToken();

        for (long amount : new long[] {Money.MAX + 1, 0, -5}) {
            assertThat(deposit(operator, account, amount).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(jdbc.queryForObject("select count(*) from ledger_entries", Integer.class)).isZero();
    }

    @Test
    void cliente_cannot_deposit_403() {
        String ana = clientToken("ana@x.com");

        assertThat(deposit(ana, open(ana), 100).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void freeze_then_unfreeze_changes_status_and_both_are_audited() {
        long account = open(clientToken("ana@x.com"));
        String operator = operatorToken();

        ResponseEntity<Map> frozen = call(HttpMethod.POST, "/api/v1/accounts/" + account + "/freeze", operator, null, Map.class);
        assertThat(frozen.getBody().get("status")).isEqualTo("FROZEN");
        ResponseEntity<Map> unfrozen = call(HttpMethod.POST, "/api/v1/accounts/" + account + "/unfreeze", operator, null, Map.class);
        assertThat(unfrozen.getBody().get("status")).isEqualTo("ACTIVE");

        assertThat(jdbc.queryForList("select action from audit_log where target = ? and action in ('FREEZE', 'UNFREEZE') order by id", String.class, "account:" + account))
                .containsExactly("FREEZE", "UNFREEZE");
    }

    @Test
    void deposit_to_nonexistent_account_is_404() {
        assertThat(deposit(operatorToken(), 999999, 100).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
