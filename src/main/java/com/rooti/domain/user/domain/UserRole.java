package com.rooti.domain.user.domain;

/**
 * Single source of truth for user roles. Stored as VARCHAR in the {@code users} table.
 *
 * <p>The Spring Security authority string is {@code "ROLE_" + name()}.
 */
public enum UserRole {
    ADMIN,
    CHARGER,
    WORKER,
    CAREGIVER
}
