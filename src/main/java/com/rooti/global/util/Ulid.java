package com.rooti.global.util;

import com.github.f4b6a3.ulid.UlidCreator;

/**
 * Thin wrapper around {@code ulid-creator}.
 *
 * <p>Used wherever we need a sortable, opaque public identifier – mostly for document filenames
 * and external reference codes. Database PKs intentionally stay as {@code BIGINT IDENTITY} for
 * join performance.
 */
public final class Ulid {

    private Ulid() {}

    public static String next() {
        return UlidCreator.getUlid().toString();
    }
}
