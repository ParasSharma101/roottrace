package com.roottrace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final WebhookApiKeyFilter webhookApiKeyFilter;
    private final BreakGlassAuthenticationFilter breakGlassAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            WebhookApiKeyFilter webhookApiKeyFilter,
            BreakGlassAuthenticationFilter breakGlassAuthenticationFilter,
            ObjectMapper objectMapper
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.webhookApiKeyFilter = webhookApiKeyFilter;
        this.breakGlassAuthenticationFilter = breakGlassAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), Map.of(
                                    "error", "UNAUTHORIZED",
                                    "message", authException.getMessage() != null ? authException.getMessage() : "Authentication required"
                            ));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), Map.of(
                                    "error", "FORBIDDEN",
                                    "message", "Insufficient permissions for this operation"
                            ));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/invitations/**", "/api/v1/auth/invitations", "/api/v1/auth/email/status", "/api/v1/auth/email/verification", "/api/v1/auth/email/verify").authenticated()
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/workspaces", "/api/v1/auth/select-workspace").authenticated()
                        .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers(org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.toAnyEndpoint()).denyAll()
                        .requestMatchers("/actuator", "/actuator/**").denyAll()
                        .requestMatchers("/", "/api/v1/auth/**", "/api/v1/simulation/**").permitAll()
                        .requestMatchers("/api/v1/workspace/**").hasAnyRole("VIEWER", "SRE", "ADMIN")
                        .requestMatchers("/api/v1/webhooks/**").hasAnyRole("INGESTION_SERVICE", "ADMIN", "SRE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/incidents/**").hasAnyRole("VIEWER", "SRE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/incidents/**").hasAnyRole("SRE", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/incidents/**").hasAnyRole("SRE", "ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(webhookApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(breakGlassAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
