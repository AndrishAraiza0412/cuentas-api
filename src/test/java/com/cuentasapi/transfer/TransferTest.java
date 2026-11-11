package com.cuentasapi.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuentasapi.common.Money;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TransferTest extends TransferSupport {

    @Test
    void transfer_moves_money_and_writes_two_entries() {
        ResponseEntity<Map> response = transfer(ana, anaAccount, benAccount, 2_500);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("COMPLETED");
        assertThat(balance(anaAccount)).isEqualTo(7_500);
        assertThat(balance(benAccount)).isEqualTo(2_500);
        long transferId = ((Number) response.getBody().get("id")).longValue();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select account_id, type, amount, balance_after from ledger_entries where transfer_id = ? order by id", transferId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("account_id", anaAccount).containsEntry("type", "DEBIT")
                .containsEntry("amount", 2_500L).containsEntry("balance_after", 7_500L);
        assertThat(rows.get(1)).containsEntry("account_id", benAccount).containsEntry("type", "CREDIT")
                .containsEntry("amount", 2_500L).containsEntry("balance_after", 2_500L);
    }

    @Test
    void insufficient_balance_is_422_and_changes_nothing() {
        int before = entryCount();

        assertThat(transfer(ana, anaAccount, benAccount, 10_001).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(balance(anaAccount)).isEqualTo(10_000);
        assertThat(balance(benAccount)).isZero();
        assertThat(entryCount()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from transfers", Integer.class)).isZero();
    }

    @Test
    void same_account_is_400() {
        assertThat(transfer(ana, anaAccount, anaAccount, 100).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(balance(anaAccount)).isEqualTo(10_000);
    }

    @Test
    void target_does_not_exist_is_404_and_changes_nothing() {
        assertThat(transfer(ana, anaAccount, 999_999, 100).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(balance(anaAccount)).isEqualTo(10_000);
    }

    @Test
    void frozen_source_is_409() {
        call(HttpMethod.POST, "/api/v1/accounts/" + anaAccount + "/freeze", operator, null, Map.class);

        assertThat(transfer(ana, anaAccount, benAccount, 100).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void frozen_target_is_409_and_changes_nothing() {
        call(HttpMethod.POST, "/api/v1/accounts/" + benAccount + "/freeze", operator, null, Map.class);
        int before = entryCount();

        assertThat(transfer(ana, anaAccount, benAccount, 100).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(balance(anaAccount)).isEqualTo(10_000);
        assertThat(balance(benAccount)).isZero();
        assertThat(entryCount()).isEqualTo(before);
    }

    @Test
    void source_account_of_someone_else_is_404() {
        assertThat(transfer(ben, anaAccount, benAccount, 100).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(balance(anaAccount)).isEqualTo(10_000);
    }

    @Test
    void amount_zero_negative_or_above_max_is_400() {
        for (long amount : new long[] {0, -1, Money.MAX + 1}) {
            assertThat(transfer(ana, anaAccount, benAccount, amount).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(balance(anaAccount)).isEqualTo(10_000);
    }

    @Test
    void failed_transfer_is_audited() {
        transfer(ana, anaAccount, benAccount, 10_001);

        Map<String, Object> row = jdbc.queryForMap(
                "select actor_id, outcome from audit_log where action = 'TRANSFER' order by id desc limit 1");
        assertThat(row.get("outcome").toString()).startsWith("FAILED");
        assertThat(((Number) row.get("actor_id")).longValue())
                .isEqualTo(jdbc.queryForObject("select id from users where email = 'ana@x.com'", Long.class));
    }

    @Test
    void get_transfer_by_owner_and_operator_ok_by_stranger_404() {
        long id = ((Number) transfer(ana, anaAccount, benAccount, 100).getBody().get("id")).longValue();

        assertThat(call(HttpMethod.GET, "/api/v1/transfers/" + id, ana, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/api/v1/transfers/" + id, operator, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/api/v1/transfers/" + id, ben, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void missing_fields_or_a_string_amount_are_400_not_zero_or_coerced() {
        ResponseEntity<Map> noTarget = call(HttpMethod.POST, "/api/v1/transfers", ana,
                Map.of("sourceAccountId", anaAccount, "amount", 100), Map.class, Map.of("Idempotency-Key", "m-1"));
        ResponseEntity<Map> noAmount = call(HttpMethod.POST, "/api/v1/transfers", ana,
                Map.of("sourceAccountId", anaAccount, "targetAccountId", benAccount), Map.class, Map.of("Idempotency-Key", "m-2"));
        ResponseEntity<Map> stringAmount = call(HttpMethod.POST, "/api/v1/transfers", ana,
                Map.of("sourceAccountId", anaAccount, "targetAccountId", benAccount, "amount", "100"), Map.class,
                Map.of("Idempotency-Key", "m-3"));

        assertThat(noTarget.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(noAmount.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stringAmount.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(balance(anaAccount)).isEqualTo(10_000);
    }
}
