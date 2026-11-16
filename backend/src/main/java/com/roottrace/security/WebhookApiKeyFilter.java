package com.roottrace.security;

import com.roottrace.security.service.IngestionKeyService;
import java.util.Optional;
import java.util.UUID;
import com.roottrace.domain.model.Workspace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class WebhookApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-RootTrace-Key";

    private final String configuredApiKey;
    private final IngestionKeyService ingestionKeys;

    public WebhookApiKeyFilter(SecuritySettings settings, IngestionKeyService ingestionKeys) {
        this.configuredApiKey = settings.getIngestionKey();
        this.ingestionKeys = ingestionKeys;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/v1/webhooks/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && request.getHeader("Authorization") != null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Use one authentication credential");
            return;
        }

        if (apiKey != null) {
            SecurityContextHolder.clearContext();
            Optional<UUID> workspace = Optional.empty();
            if (StringUtils.hasText(apiKey) && MessageDigest.isEqual(
                    configuredApiKey.getBytes(StandardCharsets.UTF_8), apiKey.getBytes(StandardCharsets.UTF_8))) {
                workspace = Optional.of(Workspace.LEGACY_WORKSPACE_ID);
            } else {
                workspace = ingestionKeys.resolveWorkspace(apiKey);
            }
            if (workspace.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Invalid ingestion credential\"}");
                return;
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    new IngestionIdentity(workspace.get()),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_INGESTION_SERVICE"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
