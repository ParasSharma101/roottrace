package com.roottrace.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class BreakGlassAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BreakGlassAuthenticationFilter.class);
    public static final String BREAK_GLASS_HEADER = "X-RootTrace-BreakGlass";

    private final String configuredBreakGlassToken;

    public BreakGlassAuthenticationFilter(
            @Value("${roottrace.security.break-glass.token:roottrace_emergency_breakglass_token}") String configuredBreakGlassToken
    ) {
        this.configuredBreakGlassToken = configuredBreakGlassToken;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String breakGlassToken = request.getHeader(BREAK_GLASS_HEADER);

        if (StringUtils.hasText(breakGlassToken) && configuredBreakGlassToken.equals(breakGlassToken.trim())) {
            log.warn("[SECURITY ALERT] Emergency Break-Glass token used to authenticate request from IP [{}] to URI [{}]",
                    request.getRemoteAddr(), request.getRequestURI());

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "emergency-breakglass@roottrace.internal",
                    null,
                    List.of(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("ROLE_SRE")
                    )
            );
            request.setAttribute("isBreakGlass", true);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
