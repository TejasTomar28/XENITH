package com.fraudshield.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.backend.auth.ApplicationRole;
import com.fraudshield.backend.auth.UserAccount;
import com.fraudshield.backend.config.SecurityProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-jwt-secret-that-is-at-least-32-characters";
    private static final Instant ISSUED_AT = Instant.parse("2026-03-04T08:35:00Z");

    @Test
    void issuesAndValidatesSignedToken() {
        JwtService service = serviceAt(ISSUED_AT, 300);
        UserAccount user = user();

        JwtPrincipal principal = service.validate(service.issueToken(user));

        assertThat(principal.username()).isEqualTo("analyst");
        assertThat(principal.role()).isEqualTo(ApplicationRole.FRAUD_ANALYST);
    }

    @Test
    void rejectsExpiredToken() {
        String token = serviceAt(ISSUED_AT, 60).issueToken(user());
        JwtService laterValidator = serviceAt(ISSUED_AT.plusSeconds(61), 60);

        assertThatThrownBy(() -> laterValidator.validate(token))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessage("The access token is invalid or expired");
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = serviceAt(ISSUED_AT, 300);
        String token = service.issueToken(user());
        char replacement = token.endsWith("A") ? 'B' : 'A';
        String tampered = token.substring(0, token.length() - 1) + replacement;

        assertThatThrownBy(() -> service.validate(tampered))
                .isInstanceOf(InvalidJwtException.class);
    }

    private static JwtService serviceAt(Instant instant, long expirationSeconds) {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt(SECRET, "fraudshield-test", expirationSeconds),
                new SecurityProperties.Gateway("test-gateway-key-at-least-24-characters"));
        return new JwtService(properties, new ObjectMapper(), Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static UserAccount user() {
        return UserAccount.create(
                "analyst",
                "$2a$12$not-a-real-hash-for-this-unit-test-only",
                ApplicationRole.FRAUD_ANALYST,
                true,
                ISSUED_AT);
    }
}
