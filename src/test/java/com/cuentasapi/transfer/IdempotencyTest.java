package com.cuentasapi.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class IdempotencyTest extends TransferSupport {

    @Test
    void missing_key_is_400() {
        ResponseEntity<Map> response = call(HttpMethod.POST, "/api/v1/transfers", ana,
                Map.of("sourceAccountId", anaAccount, "targetAccountId", benAccount, "amount", 100), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(balance(anaAccount)).isEqualTo(10_000);
    }

    @Test
    void blank_or_longer_than_80_key_is_400() {
        for (String key : new String[] {" ", "x".repeat(81)}) {
            assertThat(transferWithKey(ana, key, anaAccount, benAccount, 100).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(transferWithKey(ana, "x".repeat(80), anaAccount, benAccount, 100).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void same_key_and_body_returns_the_same_response_and_applies_once() {
        ResponseEntity<Map> first = transferWithKey(ana, "k-1", anaAccount, benAccount, 300);
        ResponseEntity<Map> second = transferWithKey(ana, "k-1", anaAccount, benAccount, 300);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(jdbc.queryForObject("select count(*) from transfers", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ledger_entries where type = 'DEBIT'", Integer.class)).isEqualTo(1);
        assertThat(balance(anaAccount)).isEqualTo(9_700);
        assertThat(balance(benAccount)).isEqualTo(300);
    }

    @Test
    void same_key_with_a_different_amount_is_422() {
        transferWithKey(ana, "k-2", anaAccount, benAccount, 300);

        ResponseEntity<Map> second = transferWithKey(ana, "k-2", anaAccount, benAccount, 301);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(balance(anaAccount)).isEqualTo(9_700);
    }

    @Test
    void same_key_for_two_different_users_is_independent() {
        call(HttpMethod.POST, "/api/v1/accounts/" + benAccount + "/deposits", operator, Map.of("amount", 1_000), Map.class);

        assertThat(transferWithKey(ana, "shared", anaAccount, benAccount, 100).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(transferWithKey(ben, "shared", benAccount, anaAccount, 100).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject("select count(*) from transfers", Integer.class)).isEqualTo(2);
    }

    @Test
    void key_is_reusable_after_a_failed_attempt() {
        assertThat(transferWithKey(ben, "retry", benAccount, anaAccount, 500).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        call(HttpMethod.POST, "/api/v1/accounts/" + benAccount + "/deposits", operator, Map.of("amount", 1_000), Map.class);

        assertThat(transferWithKey(ben, "retry", benAccount, anaAccount, 500).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(balance(benAccount)).isEqualTo(500);
    }
}
