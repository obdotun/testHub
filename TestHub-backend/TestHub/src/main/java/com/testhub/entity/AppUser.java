package com.testhub.entity;

import com.testhub.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Utilisateur de la plateforme TestHub.
 *
 * Rôles hiérarchiques : VIEWER < QA_ENGINEER < QA_LEAD < ADMIN
 * Premier admin créé automatiquement au démarrage si aucun n'existe.
 */
@Entity
@Table(name = "app_user") // "user" est un mot réservé SQL
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(nullable = false)
    private String password; // bcrypt

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.VIEWER;

    /** Forcé à changer le password à la première connexion */
    @Builder.Default
    private boolean firstLogin = true;

    /** Compte actif — un admin peut désactiver sans supprimer */
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}