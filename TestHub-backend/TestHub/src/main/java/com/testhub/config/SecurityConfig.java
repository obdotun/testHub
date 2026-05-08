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
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // ── Reports publics ───────────────────────────────────────
                        // permitAll car :
                        //   1. Les iframes et images ne peuvent pas envoyer de headers Auth
                        //   2. Le runId est une protection implicite suffisante
                        //   3. Les rapports sont des fichiers statiques générés
                        .requestMatchers(HttpMethod.GET, "/api/reports/**").permitAll()

                        // ── ADMIN seulement ───────────────────────────────────────
                        .requestMatchers("/api/users/**").hasRole("ADMIN")

                        // ── QA_LEAD et au-dessus ──────────────────────────────────
                        .requestMatchers(HttpMethod.POST,   "/api/projects/**").hasAnyRole(
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
                .headers(h -> h.frameOptions(fo -> fo.disable()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}