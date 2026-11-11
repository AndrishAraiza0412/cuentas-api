package com.cuentasapi.common;

/** Amounts are whole centavos. One operation may move at most {@link #MAX} (100 million MXN). */
public final class Money {

    public static final long MAX = 10_000_000_000L;

    private Money() {
    }

    public static void requireValid(long amount) {
        if (amount < 1 || amount > MAX) {
            throw ApiException.badRequest("Amount must be between 1 and " + MAX + " centavos");
        }
    }
}
