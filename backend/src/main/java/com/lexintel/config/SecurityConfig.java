package com.lexintel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * EXECUTION PATH: every request to {@code /api/v1/**} and {@code /admin/**} must carry a valid
 * JWT bearer token, verified against {@code issuer-uri} (Clerk, in this app — see the frontend
 * {@code proxy.ts}, which attaches the Clerk session token before forwarding requests here).
 * SAD PATH: a missing/invalid/expired token is rejected by Spring Security's OAuth2 resource
 * server filter before the request ever reaches a controller — look here first (and in
 * {@code application.yml}'s {@code issuer-uri}) when requests are failing with 401/403.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                    // 1. Publicly accessible endpoints (Preflight OPTIONS, health checks, Swagger docs)
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/actuator/health/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                    // 2. Protected administrative & business API endpoints
                    .requestMatchers("/admin/**").authenticated() // or .hasRole("ADMIN") if using role claims
                    .requestMatchers("/api/v1/**").authenticated()

                    // 3. Fallback rule for all other endpoints
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

            return http.build();
        }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Normalizes issuer string to guarantee JWKS discovery works regardless of trailing slash
        String cleanIssuer = issuerUri.endsWith("/") ? issuerUri.substring(0, issuerUri.length() - 1) : issuerUri;
        return JwtDecoders.fromIssuerLocation(cleanIssuer);
    }
}