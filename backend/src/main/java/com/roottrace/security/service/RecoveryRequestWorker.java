package com.roottrace.security.service;

import com.roottrace.infrastructure.mail.MailSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class RecoveryRequestWorker {
    private final RecoveryInbox inbox;
    private final MailSettings mail;
    public RecoveryRequestWorker(RecoveryInbox inbox,MailSettings mail) { this.inbox=inbox; this.mail=mail; }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public boolean processOne() {
        if(!mail.enabled) return false;
        var claim=inbox.claim(); if(claim.isEmpty()) return false;
        try { inbox.process(claim.get()); } catch(Exception error) { inbox.failed(claim.get()); }
        return true;
    }
}
