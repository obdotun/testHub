package com.testhub.config;

import com.testhub.entity.AppUser;
import com.testhub.repository.UserRepository;
import com.testhub.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtre JWT — intercepte chaque requête HTTP et valide le token.
 *
 * Le token peut être fourni de deux façons :
 *   1. Header Authorization: Bearer {token}  → requêtes API normales
 *   2. Query param ?token={token}            → iframes (report.html, log.html)
 *      car les iframes ne peuvent pas envoyer de headers HTTP custom.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService     jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        if (!jwtService.isValid(token)) {
            chain.doFilter(request, response);
            return;
        }

        String username = jwtService.extractUsername(token);
        String role     = jwtService.extractRole(token);

        // Vérifier que l'utilisateur existe encore et est actif
        AppUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !user.isActive()) {
            chain.doFilter(request, response);
            return;
        }

        // Injecter dans le SecurityContext
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );

        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

    /**
     * Extrait le token JWT depuis :
     *   1. Header Authorization: Bearer {token}
     *   2. Query parameter ?token={token}  (fallback pour les iframes)
     */
    private String extractToken(HttpServletRequest request) {
        // Priorité 1 : header Authorization
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Priorité 2 : query param (iframes report.html / log.html)
        String queryToken = request.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken;
        }

        return null;
    }
}