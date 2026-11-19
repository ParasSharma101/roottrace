package com.roottrace.security;

import com.roottrace.infrastructure.mail.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailSecurityTest {
    private SecuritySettings credentials() {
        var settings=mock(SecuritySettings.class);
        when(settings.getJwtSecret()).thenReturn("separate-test-signing-secret-at-least-32-bytes");
        when(settings.getIngestionKey()).thenReturn("separate-test-ingestion-secret-at-least-32-bytes");
        return settings;
    }
    private MockEnvironment environment() {
        return new MockEnvironment().withProperty("roottrace.mail.enabled","true")
                .withProperty("roottrace.mail.from","roottrace@example.com").withProperty("roottrace.mail.public-base-url","https://app.example.com")
                .withProperty("roottrace.mail.encryption-key",Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .withProperty("spring.mail.host","smtp.example.com").withProperty("spring.mail.username","fixture-user").withProperty("spring.mail.password","fixture-password")
                .withProperty("spring.mail.properties.mail.smtp.auth","true").withProperty("spring.mail.properties.mail.smtp.starttls.enable","true")
                .withProperty("spring.mail.properties.mail.smtp.starttls.required","true").withProperty("spring.mail.properties.mail.smtp.ssl.checkserveridentity","true");
    }
    @Test
    void productionMailRequiresHttpsTlsAuthenticationAndIndependentEncryption() {
        assertThat(new MailSettings(environment(),credentials()).enabled).isTrue();
        for(String[] invalid:List.of(new String[]{"roottrace.mail.public-base-url","http://app.example.com"},
                new String[]{"spring.mail.properties.mail.smtp.starttls.required","false"},new String[]{"spring.mail.password",""},
                new String[]{"roottrace.mail.encryption-key","short"},new String[]{"spring.mail.properties.mail.smtp.ssl.checkserveridentity","false"})) {
            var env=environment().withProperty(invalid[0],invalid[1]); env.setActiveProfiles("dev","prod");
            assertThatThrownBy(() -> new MailSettings(env,credentials())).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test
    void ciphertextIsBoundToMessagePurposeAndRecipient() {
        var cipher=new MailCipher(new MailSettings(environment(),credentials()));
        UUID id=UUID.randomUUID(); String secret="verification-link-rt_ver_fixture";
        var encrypted=cipher.encrypt(id,"VERIFY_EMAIL","recipient@example.com",secret);
        assertThat(cipher.decrypt(id,"VERIFY_EMAIL","recipient@example.com",encrypted.body(),encrypted.nonce())).isEqualTo(secret);
        assertThatThrownBy(() -> cipher.decrypt(id,"VERIFY_EMAIL","different@example.com",encrypted.body(),encrypted.nonce())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(UUID.randomUUID(),"VERIFY_EMAIL","recipient@example.com",encrypted.body(),encrypted.nonce())).isInstanceOf(IllegalStateException.class);
        assertThat(encrypted.toString()).doesNotContain(secret);
    }
}
