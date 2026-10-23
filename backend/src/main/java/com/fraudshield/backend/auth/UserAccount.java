package com.fraudshield.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    private UserAccount(
            UUID id,
            String username,
            String passwordHash,
            ApplicationRole role,
            boolean enabled,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.username = normalizeUsername(username);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.role = Objects.requireNonNull(role);
        this.enabled = enabled;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static UserAccount create(
            String username,
            String passwordHash,
            ApplicationRole role,
            boolean enabled,
            Instant createdAt) {
        return new UserAccount(UUID.randomUUID(), username, passwordHash, role, enabled, createdAt);
    }

    public static String normalizeUsername(String username) {
        String normalized = Objects.requireNonNull(username).trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 100) {
            throw new IllegalArgumentException("Username must contain between 1 and 100 characters");
        }
        return normalized;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public ApplicationRole getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
