package com.cuentasapi;

import com.cuentasapi.auth.OperatorSeeder;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Base for every integration test: one PostgreSQL container shared by the whole test run. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

    // Started once per JVM (not per test class) so the cached Spring context keeps a valid URL.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("truncate audit_log, idempotency_keys, ledger_entries, transfers, accounts, users restart identity cascade");
    }

    @Autowired
    protected OperatorSeeder operatorSeeder;

    /** Calls the API with an optional bearer token and optional JSON body. */
    protected <T> ResponseEntity<T> call(HttpMethod method, String path, String token, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), type);
    }

    /** Registers a customer (or reuses it) and returns a valid token. */
    protected String clientToken(String email) {
        Map<String, String> credentials = Map.of("email", email, "password", "correct-horse-1");
        rest.postForEntity("/api/v1/auth/register", credentials, Map.class);
        return (String) rest.postForEntity("/api/v1/auth/login", credentials, Map.class).getBody().get("token");
    }

    protected String operatorToken() {
        operatorSeeder.seed("operator@bank.test", "operator-pass-1");
        Map<String, String> credentials = Map.of("email", "operator@bank.test", "password", "operator-pass-1");
        return (String) rest.postForEntity("/api/v1/auth/login", credentials, Map.class).getBody().get("token");
    }
}
