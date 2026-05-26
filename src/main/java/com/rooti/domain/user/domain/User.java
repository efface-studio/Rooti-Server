package com.rooti.domain.user.domain;

import com.rooti.global.audit.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Aggregate root for everyone who can authenticate.
 *
 * <p>The Django project had {@code User} + a per-role profile table; we collapse that here to a
 * single {@code users} table with a discriminating {@link UserRole}. Each role-specific table
 * (worker, caregiver, charger) hangs off this aggregate by {@code user_id}.
 */
@Entity
@Table(
        name = "users",
        indexes = {@Index(name = "idx_users_email", columnList = "email"), @Index(name = "idx_users_role", columnList = "role")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String username;

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Builder
    private User(
            String username,
            String email,
            String passwordHash,
            String name,
            String phoneNumber,
            UserRole role,
            Boolean enabled) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.enabled = enabled == null || enabled;
    }

    // ----- Domain behaviour -----------------------------------------------------
    public void markLoggedIn() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
    }

    public void updateProfile(String name, String phoneNumber, String email) {
        if (name != null) this.name = name;
        if (phoneNumber != null) this.phoneNumber = phoneNumber;
        if (email != null) this.email = email;
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }
}
