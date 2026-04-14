package com.testhub.controller;

import com.testhub.dto.AuthDto;
import com.testhub.entity.AppUser;
import com.testhub.service.JwtService;
import com.testhub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService  jwtService;

    /**
     * POST /api/auth/login
     * Public — retourne un token JWT + infos utilisateur.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthDto.LoginResponse> login(
            @Valid @RequestBody AuthDto.LoginRequest req) {

        AppUser user  = userService.authenticate(req.getUsername(), req.getPassword());
        String  token = jwtService.generateToken(
                user.getUsername(), user.getRole().name());

        AuthDto.LoginResponse response = new AuthDto.LoginResponse();
        response.setToken(token);
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setFirstLogin(user.isFirstLogin());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/auth/me
     * Retourne les infos de l'utilisateur connecté.
     */
    @GetMapping("/me")
    public ResponseEntity<AuthDto.UserResponse> me(Principal principal) {
        return ResponseEntity.ok(
                userService.toResponse(
                        userService.authenticate(principal.getName(), null)));
    }

    /**
     * POST /api/auth/change-password
     * Changer son propre mot de passe.
     * Obligatoire au premier login (firstLogin = true).
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            Principal principal,
            @Valid @RequestBody AuthDto.ChangePasswordRequest req) {

        userService.changePassword(
                principal.getName(),
                req.getCurrentPassword(),
                req.getNewPassword());

        return ResponseEntity.ok().build();
    }
}