package com.cuentasapi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuentasapi.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class LoginConcurrencyTest extends IntegrationTest {

    /** More simultaneous failures than the 10-connection pool: each must hold at most one connection. */
    @Test
    void twenty_simultaneous_failed_logins_are_all_401_and_all_audited() throws Exception {
        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return rest.postForEntity("/api/v1/auth/login",
                        Map.of("email", "nobody@x.com", "password", "wrong-password"), Map.class);
            }));
        }
        ready.await();
        go.countDown();

        for (Future<ResponseEntity<Map>> future : futures) {
            assertThat(future.get(90, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        pool.shutdown();
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where action = 'LOGIN' and outcome = 'FAILED'", Integer.class))
                .isEqualTo(attempts);
    }
}
