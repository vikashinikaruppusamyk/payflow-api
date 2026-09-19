package com.example.payflow.security;

import com.example.payflow.entity.Role;
import com.example.payflow.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies HMAC-SHA256 signed JWTs. The token carries the user id, UPI ID (subject) and role,
 * so each request is authenticated without a server-side session or a database lookup.
 */
@Service
public class JwtService {
    private static final int MIN_SECRET_BYTES = 32; // HS256 needs a key of at least 256 bits
    private static final String ISSUER = "payflow-api";

    private final SecretKey signingKey;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(@Value("${payflow.jwt.secret:}") String secret,
                      @Value("${payflow.jwt.expiration:PT1H}") Duration expiration,
                      Clock clock) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            // Fail at startup rather than run with a weak or missing key
            throw new IllegalStateException("payflow.jwt.secret must be at least " + MIN_SECRET_BYTES
                    + " characters. Set JWT_SECRET in local.properties or as an environment variable.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
        this.clock = clock;
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getUpiId())
                .claim("uid", user.getUserId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies the signature, issuer and expiry, then returns the caller.
     *
     * @throws JwtException if the token is expired, malformed, tampered with or signed with another key
     */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = claims.get("uid", Long.class);
        Role role = Role.valueOf(claims.get("role", String.class));
        return new AuthenticatedUser(userId, claims.getSubject(), role);
    }

    public Duration getExpiration() {
        return expiration;
    }
}
