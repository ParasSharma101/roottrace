package com.roottrace.infrastructure.mail;

import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;

@Component
public class MailCipher {
    private final MailSettings settings;
    private final SecureRandom random=new SecureRandom();
    public MailCipher(MailSettings settings) { this.settings=settings; }
    public Encrypted encrypt(UUID id,String purpose,String recipient,String body) {
        byte[] nonce=new byte[12]; random.nextBytes(nonce);
        return new Encrypted(crypt(Cipher.ENCRYPT_MODE,id,purpose,recipient,nonce,body.getBytes(StandardCharsets.UTF_8)),nonce);
    }
    public String decrypt(UUID id,String purpose,String recipient,byte[] encrypted,byte[] nonce) {
        return new String(crypt(Cipher.DECRYPT_MODE,id,purpose,recipient,nonce,encrypted),StandardCharsets.UTF_8);
    }
    private byte[] crypt(int mode,UUID id,String purpose,String recipient,byte[] nonce,byte[] input) {
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode,new SecretKeySpec(settings.key(),"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD((id+"\n"+purpose+"\n"+recipient).getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(input);
        } catch(Exception failure) { throw new IllegalStateException("Mail payload cryptography failed"); }
    }
    public record Encrypted(byte[] body,byte[] nonce) { @Override public String toString() { return "EncryptedMail[redacted]"; } }
}
