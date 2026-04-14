package com.testhub.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

/**
 * Génère et valide les tokens JWT.
 *
 * Payload du token :
 *   sub   → username
 *   role  → VIEWER | QA_ENGINEER | QA_LEAD | ADMIN
 *   iat   → date d'émission
 *   exp   → date d'expiration
 */
@Slf4j
@Service
public class JwtService {

    @Value("${testhub.jwt.secret}")
    private String secret;

    @Value("${testhub.jwt.expiration-hours:8}")
    private int expirationHours;

    public String generateToken(String username, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + (long) expirationHours * 3600 * 1000);

        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public boolean isValid(String token) {
        try {
            Claims claims = getClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            log.debug("Token invalide : {}", e.getMessage());
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getKey() {
        byte[] keyBytes = secret.getBytes();
        // S'assurer que la clé fait au moins 256 bits pour HS256
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret trop court — minimum 32 caractères dans testhub.jwt.secret");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}