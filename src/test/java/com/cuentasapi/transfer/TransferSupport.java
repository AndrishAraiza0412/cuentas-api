package com.cuentasapi.transfer;

import com.cuentasapi.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** Two customers with one account each, and ana's account funded with 10,000 centavos. */
abstract class TransferSupport extends IntegrationTest {

    String ana;
    String ben;
    String operator;
    long anaAccount;
    long benAccount;

    private long open(String token) {
        return ((Number) call(HttpMethod.POST, "/api/v1/accounts", token, null, Map.class).getBody().get("id")).longValue();
    }

    private void deposit(long account, long amount) {
        call(HttpMethod.POST, "/api/v1/accounts/" + account + "/deposits", operator, Map.of("amount", amount), Map.class);
    }

    ResponseEntity<Map> transfer(String token, long source, long target, long amount) {
        return transferWithKey(token, UUID.randomUUID().toString(), source, target, amount);
    }

    ResponseEntity<Map> transferWithKey(String token, String key, long source, long target, long amount) {
        return call(HttpMethod.POST, "/api/v1/transfers", token,
                Map.of("sourceAccountId", source, "targetAccountId", target, "amount", amount), Map.class,
                Map.of("Idempotency-Key", key));
    }

    long balance(long account) {
        return jdbc.queryForObject("select balance from accounts where id = ?", Long.class, account);
    }

    int entryCount() {
        return jdbc.queryForObject("select count(*) from ledger_entries", Integer.class);
    }

    @BeforeEach
    void accounts() {
        ana = clientToken("ana@x.com");
        ben = clientToken("ben@x.com");
        operator = operatorToken();
        anaAccount = open(ana);
        benAccount = open(ben);
        deposit(anaAccount, 10_000);
    }
}
