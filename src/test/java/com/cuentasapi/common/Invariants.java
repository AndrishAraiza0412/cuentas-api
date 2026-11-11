package com.cuentasapi.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** The properties that must hold after any sequence of requests, concurrent or not. */
public final class Invariants {

    private Invariants() {
    }

    /**
     * Every balance equals the sum of its signed ledger entries, no balance is negative, and the money in the
     * system is exactly what operators deposited.
     */
    public static void assertConsistent(JdbcTemplate jdbc, long expectedTotal) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select a.id, a.balance,
                       coalesce(sum(case when e.type = 'CREDIT' then e.amount else -e.amount end), 0) as ledger
                from accounts a left join ledger_entries e on e.account_id = a.id
                group by a.id, a.balance""");
        long total = 0;
        for (Map<String, Object> row : rows) {
            long balance = ((Number) row.get("balance")).longValue();
            assertThat(balance).as("balance of account %s", row.get("id")).isGreaterThanOrEqualTo(0);
            assertThat(balance).as("balance equals ledger sum for account %s", row.get("id"))
                    .isEqualTo(((Number) row.get("ledger")).longValue());
            total += balance;
        }
        assertThat(total).as("total money in the system").isEqualTo(expectedTotal);
    }
}
