package com.rooti.global.jwt;

import java.util.List;

/** Minimal claim subset surfaced to the rest of the app. */
public record JwtPayload(long userId, String username, List<String> roles) {}
