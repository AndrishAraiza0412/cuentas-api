package com.cuentasapi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SchemaTest extends IntegrationTest {

    private long insertUser() {
        return jdbc.queryForObject(
                "insert into users (email, password_hash, role) values ('a@x.com', 'h', 'CLIENTE') returning id",
                Long.class);
    }

    @Test
    void schema_rejects_negative_balance() {
        long userId = insertUser();

        assertThatThrownBy(() -> jdbc.update(
                "insert into accounts (user_id, number, balance) values (?, '000000000001', -1)", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void schema_enforces_unique_idempotency_key_per_user() {
        long userId = insertUser();
        jdbc.update("insert into idempotency_keys (user_id, key, request_hash) values (?, 'k1', 'h')", userId);

        assertThatThrownBy(() -> jdbc.update(
                "insert into idempotency_keys (user_id, key, request_hash) values (?, 'k1', 'h2')", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
