package com.roottrace.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(SecuritySettings settings) {
        this.secretKey = Keys.hmacShaKeyFor(settings.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = settings.getJwtExpirationMs();
    }

    public String generateToken(UUID userId, UUID workspaceId) {
        Date now = new Date();
        return Jwts.builder().subject(userId.toString())
                .claim("sessionVersion", 2)
                .claim("workspaceId", workspaceId == null ? null : workspaceId.toString())
                .issuedAt(now).expiration(new Date(now.getTime() + expirationMs))
                .signWith(secretKey).compact();
    }

    public TokenIdentity readIdentity(String token) {
        Claims claims = getClaims(token);
        if (!Integer.valueOf(2).equals(claims.get("sessionVersion", Integer.class))) {
            throw new IllegalArgumentException("Session version no longer supported");
        }
        String workspaceId = claims.get("workspaceId", String.class);
        return new TokenIdentity(UUID.fromString(claims.getSubject()),
                workspaceId == null ? null : UUID.fromString(workspaceId));
    }

    public record TokenIdentity(UUID userId, UUID workspaceId) {}

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
