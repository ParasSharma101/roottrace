package com.roottrace.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryTokenTest {
    private static final String SIGNING="test-only-signing-credential-at-least-32-bytes";
    private JwtTokenProvider provider() {
        return new JwtTokenProvider(new SecuritySettings(new MockEnvironment()
                .withProperty("roottrace.security.jwt.secret",SIGNING)
                .withProperty("roottrace.security.ingestion.api-key","independent-ingestion-credential-at-least-32-bytes")));
    }
    @Test
    void previousVersionAndIncompleteSignedIdentityAreRejected() {
        var key=Keys.hmacShaKeyFor(SIGNING.getBytes(StandardCharsets.UTF_8));
        for(int version:List.of(2,3)) {
            String token=Jwts.builder().subject(UUID.randomUUID().toString()).claim("sessionVersion",version)
                    .expiration(new Date(System.currentTimeMillis()+60000)).signWith(key).compact();
            assertThatThrownBy(() -> provider().readIdentity(token)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test
    void passwordPolicyCountsCharactersAndPreventsBcryptByteTruncation() {
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(72))).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("😀".repeat(15))).doesNotThrowAnyException();
        for(String invalid:List.of("a".repeat(14),"a".repeat(73),"😀".repeat(19))) {
            assertThatThrownBy(() -> PasswordPolicy.validate(invalid)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
    }
}
