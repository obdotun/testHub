package com.testhub.controller;

import com.testhub.dto.AuthDto;
import com.testhub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestion des utilisateurs — ADMIN seulement.
 * L'accès est aussi protégé dans SecurityConfig.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    /** GET /api/users */
    @GetMapping
    public ResponseEntity<List<AuthDto.UserResponse>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    /** GET /api/users/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<AuthDto.UserResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    /** POST /api/users */
    @PostMapping
    public ResponseEntity<AuthDto.UserResponse> create(
            @Valid @RequestBody AuthDto.CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(req));
    }

    /** PUT /api/users/{id} */
    @PutMapping("/{id}")
    public ResponseEntity<AuthDto.UserResponse> update(
            @PathVariable Long id,
            @RequestBody AuthDto.UpdateUserRequest req) {
        return ResponseEntity.ok(userService.updateUser(id, req));
    }

    /** DELETE /api/users/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}