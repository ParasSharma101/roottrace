package com.roottrace.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class WebhookApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-RootTrace-Key";

    private final String configuredApiKey;

    public WebhookApiKeyFilter(
            @Value("${roottrace.security.ingestion.api-key:roottrace_dev_secret_key}") String configuredApiKey
    ) {
        this.configuredApiKey = configuredApiKey;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (StringUtils.hasText(apiKey) && configuredApiKey.equals(apiKey.trim())) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "ingestion-service",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_INGESTION_SERVICE"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
