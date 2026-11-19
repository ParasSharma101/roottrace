package com.roottrace.infrastructure.mail;

import com.roottrace.security.SecuritySettings;
import jakarta.mail.internet.InternetAddress;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class MailSettings {
    public final boolean enabled;
    public final String from;
    private final String publicBase;
    private final byte[] key;
    public MailSettings(Environment env,SecuritySettings security) {
        enabled=env.getProperty("roottrace.mail.enabled",Boolean.class,false);
        from=env.getProperty("roottrace.mail.from","roottrace@localhost.invalid");
        publicBase=env.getProperty("roottrace.mail.public-base-url","").replaceAll("/+$","");
        if(!enabled) { key=null; return; }
        boolean dev=env.acceptsProfiles(Profiles.of("dev & !prod"));
        try {
            URI base=URI.create(publicBase);
            if(base.getHost()==null || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null || !base.getPath().isEmpty()
                    || !("https".equals(base.getScheme()) || (dev && "http".equals(base.getScheme())))) throw new IllegalArgumentException();
            InternetAddress address=new InternetAddress(from,true); address.validate();
            if(address.getPersonal()!=null || from.chars().anyMatch(c -> c<32)) throw new IllegalArgumentException();
        } catch(Exception failure) { throw invalid("Mail sender and public application origin must be valid; HTTPS is required outside dev"); }
        String encoded=env.getProperty("roottrace.mail.encryption-key","");
        try { key=Base64.getDecoder().decode(encoded); }
        catch(IllegalArgumentException ex) { throw invalid("Mail encryption key must be Base64-encoded 32 random bytes"); }
        if(key.length!=32 || encoded.equals(security.getJwtSecret()) || encoded.equals(security.getIngestionKey())
                || MessageDigest.isEqual(key,security.getJwtSecret().getBytes(StandardCharsets.UTF_8))
                || MessageDigest.isEqual(key,security.getIngestionKey().getBytes(StandardCharsets.UTF_8))) {
            throw invalid("Mail encryption key must be 32 independent random bytes");
        }
        if(env.getProperty("spring.mail.host","").isBlank()) throw invalid("SMTP host is required when mail is enabled");
        if(!dev && (from.endsWith(".invalid") || !env.getProperty("spring.mail.properties.mail.smtp.ssl.checkserveridentity",Boolean.class,false))) throw invalid("Production mail requires a real sender and server identity verification");
        boolean ssl=env.getProperty("spring.mail.properties.mail.smtp.ssl.enable",Boolean.class,false);
        boolean tls=env.getProperty("spring.mail.properties.mail.smtp.starttls.enable",Boolean.class,false)
                && env.getProperty("spring.mail.properties.mail.smtp.starttls.required",Boolean.class,false);
        if(!dev && (!ssl && !tls)) throw invalid("Production SMTP requires SSL or required STARTTLS");
        if(!dev && (!env.getProperty("spring.mail.properties.mail.smtp.auth",Boolean.class,false)
                || env.getProperty("spring.mail.username","").isBlank() || env.getProperty("spring.mail.password","").isBlank())) {
            throw invalid("Production SMTP authentication is required");
        }
    }
    public void requireEnabled() { if(!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Email delivery is not configured"); }
    public byte[] key() { requireEnabled(); return key.clone(); }
    public String link(String path) { requireEnabled(); return publicBase+path; }
    private IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
