package com.roottrace.security.service;

import com.roottrace.security.SecuritySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Direct peer only: untrusted forwarded headers cannot bypass recovery limits. */
@Service
public class RecoveryRequestLimiter {
    private final JdbcTemplate jdbc;
    private final byte[] key;
    private final int limit;
    public RecoveryRequestLimiter(JdbcTemplate jdbc,SecuritySettings settings,
            @Value("${roottrace.recovery.requests-per-peer-window:20}") int limit) {
        if(limit<1) throw new IllegalArgumentException("Recovery peer limit must be positive");
        this.jdbc=jdbc; this.key=settings.getJwtSecret().getBytes(StandardCharsets.UTF_8); this.limit=limit;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean allow(String peer) {
        String hash;
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256"));
            hash=HexFormat.of().formatHex(mac.doFinal(("password-recovery-peer\n"+peer).getBytes(StandardCharsets.UTF_8)));
        } catch(Exception error) { throw new IllegalStateException("Recovery limiter unavailable"); }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            insert into recovery_request_limits(peer_hash,window_start,attempts)
            values (?,floor(extract(epoch from clock_timestamp())/900)::bigint,1)
            on conflict(peer_hash,window_start) do update set attempts=least(recovery_request_limits.attempts+1,?)
            returning attempts<=?
            """,Boolean.class,hash,limit+1,limit));
    }
}
