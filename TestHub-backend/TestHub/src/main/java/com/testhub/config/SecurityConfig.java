package com.testhub.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration Spring Security.
 *
 * Hiérarchie des rôles :
 *   VIEWER < QA_ENGINEER < QA_LEAD < ADMIN
 *
 * Règles d'accès par endpoint :
 *   POST /api/auth/login           → public
 *   GET  /api/auth/me              → authentifié
 *   POST /api/auth/change-password → authentifié
 *   GET  /api/projects             → tout rôle
 *   POST /api/projects             → QA_LEAD+
 *   POST /api/runs                 → QA_ENGINEER+
 *   /api/users/**                  → ADMIN seulement
 *   /ws/**                         → public (géré par STOMP)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // ── Public ───────────────────────────────────────────────
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/ws/**").permitAll()         // WebSocket
                        .requestMatchers("/h2-console/**").permitAll() // dev only

                        // ── Admin seulement ───────────────────────────────────────
                        .requestMatchers("/api/users/**").hasRole("ADMIN")

                        // ── QA_LEAD et au-dessus ──────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/projects/**").hasAnyRole(
                                "QA_LEAD", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/projects/**").hasAnyRole(
                                "QA_LEAD", "ADMIN")

                        // ── QA_ENGINEER et au-dessus ──────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/runs").hasAnyRole(
                                "QA_ENGINEER", "QA_LEAD", "ADMIN")

                        // ── Tout utilisateur authentifié ──────────────────────────
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class)

                // H2 console (dev)
                .headers(h -> h.frameOptions(fo -> fo.disable()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}