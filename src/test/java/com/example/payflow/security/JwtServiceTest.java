package com.example.payflow.security;

import com.example.payflow.entity.Role;
import com.example.payflow.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final String SECRET = "unit-test-signing-key-that-is-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");

    private static User admin() {
        User user = new User("Admin", "admin@payflow", BigDecimal.ZERO, null, "hash");
        ReflectionTestUtils.setField(user, "userId", 42L);
        user.setRole(Role.ADMIN);
        return user;
    }

    private static JwtService serviceAt(Instant instant) {
        return new JwtService(SECRET, Duration.ofHours(1), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void tokenRoundTripsUserIdUpiIdAndRole() {
        JwtService service = serviceAt(NOW);

        AuthenticatedUser user = service.parse(service.generateToken(admin()));

        assertThat(user.userId()).isEqualTo(42L);
        assertThat(user.upiId()).isEqualTo("admin@payflow");
        assertThat(user.role()).isEqualTo(Role.ADMIN);
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    void tokenExpiresAfterConfiguredLifetime() {
        String token = serviceAt(NOW).generateToken(admin());

        assertThat(serviceAt(NOW.plus(Duration.ofMinutes(59))).parse(token).upiId()).isEqualTo("admin@payflow");
        assertThatThrownBy(() -> serviceAt(NOW.plus(Duration.ofMinutes(61))).parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void modifiedPayloadFailsSignatureCheck() {
        JwtService service = serviceAt(NOW);
        String[] parts = service.generateToken(admin()).split("\\.");
        // Re-encode the payload with a different subject but keep the original signature
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(java.util.Base64.getUrlDecoder().decode(parts[1]))
                        .replace("admin@payflow", "mallory@evil").getBytes());

        assertThatThrownBy(() -> service.parse(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesToStartWithAShortSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", Duration.ofHours(1), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
        assertThatThrownBy(() -> new JwtService("", Duration.ofHours(1), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
