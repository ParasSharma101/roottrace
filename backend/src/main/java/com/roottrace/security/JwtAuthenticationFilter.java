package com.roottrace.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.roottrace.security.service.WorkspaceAuthenticationService;
import io.jsonwebtoken.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final WorkspaceAuthenticationService workspaceAuthentication;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, WorkspaceAuthenticationService workspaceAuthentication) {
        this.tokenProvider = tokenProvider;
        this.workspaceAuthentication = workspaceAuthentication;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String jwt = resolveToken(request);

        if (StringUtils.hasText(jwt)) {
            JwtTokenProvider.TokenIdentity tokenIdentity = null;
            try {
                tokenIdentity = tokenProvider.readIdentity(jwt);
            } catch (JwtException | IllegalArgumentException ignored) {
                // Invalid, expired, and previous-version sessions never establish identity.
            }
            if (tokenIdentity != null) {
                workspaceAuthentication.authenticate(tokenIdentity.userId(), tokenIdentity.workspaceId())
                        .ifPresent(identity -> {
                            var authorities = identity.roles().stream().map(SimpleGrantedAuthority::new).toList();
                            var authentication = new UsernamePasswordAuthenticationToken(identity, null, authorities);
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
