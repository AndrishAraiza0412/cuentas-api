package com.cuentasapi.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.cuentasapi.common.Invariants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ConcurrencyTest extends TransferSupport {

    /** Starts every call at the same moment and waits at most 60 seconds for all of them. */
    private List<ResponseEntity<Map>> runTogether(List<Callable<ResponseEntity<Map>>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch ready = new CountDownLatch(calls.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
        for (Callable<ResponseEntity<Map>> call : calls) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return call.call();
            }));
        }
        ready.await();
        go.countDown();
        List<ResponseEntity<Map>> results = new ArrayList<>();
        for (Future<ResponseEntity<Map>> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    private long count(List<ResponseEntity<Map>> results, HttpStatus status) {
        return results.stream().filter(r -> r.getStatusCode() == status).count();
    }

    @Test
    void fifty_simultaneous_transfers_from_one_account() throws Exception {
        List<Callable<ResponseEntity<Map>>> calls = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            calls.add(() -> transfer(ana, anaAccount, benAccount, 300));
        }

        List<ResponseEntity<Map>> results = runTogether(calls);

        assertThat(count(results, HttpStatus.CREATED)).isEqualTo(33);
        assertThat(count(results, HttpStatus.UNPROCESSABLE_ENTITY)).isEqualTo(17);
        assertThat(balance(anaAccount)).isEqualTo(100);
        assertThat(balance(benAccount)).isEqualTo(9_900);
        Invariants.assertConsistent(jdbc, 10_000);
    }

    @Test
    void crossed_transfers_do_not_deadlock() throws Exception {
        call(HttpMethod.POST, "/api/v1/accounts/" + benAccount + "/deposits", operator, Map.of("amount", 10_000), Map.class);
        List<Callable<ResponseEntity<Map>>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            calls.add(() -> transfer(ana, anaAccount, benAccount, 10));
            calls.add(() -> transfer(ben, benAccount, anaAccount, 10));
        }

        List<ResponseEntity<Map>> results = runTogether(calls);

        assertThat(count(results, HttpStatus.CREATED)).isEqualTo(40);
        assertThat(balance(anaAccount)).isEqualTo(10_000);
        assertThat(balance(benAccount)).isEqualTo(10_000);
        Invariants.assertConsistent(jdbc, 20_000);
    }

    @Test
    void same_idempotency_key_in_parallel_applies_once() throws Exception {
        String key = UUID.randomUUID().toString();
        List<Callable<ResponseEntity<Map>>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            calls.add(() -> transferWithKey(ana, key, anaAccount, benAccount, 300));
        }

        List<ResponseEntity<Map>> results = runTogether(calls);

        assertThat(count(results, HttpStatus.CREATED)).isEqualTo(20);
        assertThat(results.stream().map(ResponseEntity::getBody).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from transfers", Integer.class)).isEqualTo(1);
        assertThat(balance(anaAccount)).isEqualTo(9_700);
        Invariants.assertConsistent(jdbc, 10_000);
    }
}
