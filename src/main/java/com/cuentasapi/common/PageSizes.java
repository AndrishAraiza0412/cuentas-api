package com.cuentasapi.common;

/** Every paged endpoint caps the page size so one request cannot read the whole table. */
public final class PageSizes {

    public static final int DEFAULT = 20;
    public static final int MAX = 100;

    private PageSizes() {
    }

    public static int cap(int size) {
        return Math.max(1, Math.min(size, MAX));
    }
}
