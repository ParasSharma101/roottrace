package com.roottrace.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static void validate(String password) {
        if(password==null || password.codePointCount(0,password.length())<15
                || password.getBytes(StandardCharsets.UTF_8).length>72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use at least 15 characters and at most 72 UTF-8 bytes for the password");
        }
    }
}
