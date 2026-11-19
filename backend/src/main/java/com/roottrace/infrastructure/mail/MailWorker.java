package com.roottrace.infrastructure.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class MailWorker {
    private final MailSettings settings;
    private final MailOutbox outbox;
    private final MailCipher cipher;
    private final JavaMailSender sender;
    public MailWorker(MailSettings settings,MailOutbox outbox,MailCipher cipher,JavaMailSender sender) {
        this.settings=settings; this.outbox=outbox; this.cipher=cipher; this.sender=sender;
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public boolean deliverOne() {
        if(!settings.enabled) return false;
        var claimed=outbox.claim(); if(claimed.isEmpty()) return false;
        var item=claimed.get();
        try {
            SimpleMailMessage message=new SimpleMailMessage(); message.setFrom(settings.from); message.setTo(item.recipient());
            message.setSubject(switch(item.purpose()) {
                case "VERIFY_EMAIL" -> "Verify your RootTrace email";
                case "WORKSPACE_INVITE" -> "RootTrace workspace invitation";
                case "PASSWORD_RESET" -> "Reset your RootTrace password";
                case "PASSWORD_CHANGED" -> "Your RootTrace password was changed";
                default -> throw new IllegalArgumentException("Unknown mail purpose");
            });
            message.setText(cipher.decrypt(item.id(),item.purpose(),item.recipient(),item.body(),item.nonce()));
            sender.send(message);
            outbox.sent(item);
        } catch(Exception error) { outbox.failed(item,error); }
        return true;
    }
}
