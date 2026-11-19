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
        return generateToken(userId,workspaceId,0);
    }

    public String generateToken(UUID userId,UUID workspaceId,long authEpoch) {
        Date now = new Date();
        return token(userId,workspaceId,UUID.randomUUID(),authEpoch,now,new Date(now.getTime()+expirationMs));
    }

    public String selectWorkspace(WorkspaceIdentity identity,UUID workspaceId) {
        if(identity.sessionId()==null || identity.sessionExpiresAt()==null) throw new IllegalArgumentException("Session required");
        return token(identity.userId(),workspaceId,identity.sessionId(),identity.authEpoch(),new Date(),Date.from(identity.sessionExpiresAt()));
    }

    private String token(UUID userId,UUID workspaceId,UUID sessionId,long epoch,Date issued,Date expires) {
        return Jwts.builder().subject(userId.toString())
                .claim("sessionVersion", 3).id(sessionId.toString()).claim("authEpoch",epoch)
                .claim("workspaceId", workspaceId == null ? null : workspaceId.toString())
                .issuedAt(issued).expiration(expires)
                .signWith(secretKey).compact();
    }

    public TokenIdentity readIdentity(String token) {
        Claims claims = getClaims(token);
        if (!Integer.valueOf(3).equals(claims.get("sessionVersion", Integer.class))) {
            throw new IllegalArgumentException("Session version no longer supported");
        }
        String workspaceId = claims.get("workspaceId", String.class);
        Long epoch=claims.get("authEpoch",Long.class);
        if(epoch==null || epoch<0 || claims.getId()==null || claims.getSubject()==null || claims.getExpiration()==null) {
            throw new IllegalArgumentException("Incomplete session identity");
        }
        return new TokenIdentity(UUID.fromString(claims.getSubject()),
                workspaceId == null ? null : UUID.fromString(workspaceId),UUID.fromString(claims.getId()),
                epoch,claims.getExpiration().toInstant());
    }

    public record TokenIdentity(UUID userId, UUID workspaceId,UUID sessionId,long authEpoch,java.time.Instant expiresAt) {}

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
