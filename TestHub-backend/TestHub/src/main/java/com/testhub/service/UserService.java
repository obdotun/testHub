package com.testhub.service;

import com.testhub.dto.AuthDto;
import com.testhub.entity.AppUser;
import com.testhub.enums.Role;
import com.testhub.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Crée le compte admin par défaut au démarrage
     * si aucun ADMIN n'existe en base.
     */
    @PostConstruct
    public void initDefaultAdmin() {
        if (!userRepository.existsByRole(Role.ADMIN)) {
            AppUser admin = AppUser.builder()
                    .username("admin")
                    .email("admin@testhub.local")
                    .fullName("Administrateur")
                    .password(passwordEncoder.encode("Admin@1234"))
                    .role(Role.ADMIN)
                    .firstLogin(true)   // forcé à changer au premier login
                    .active(true)
                    .build();
            userRepository.save(admin);
            log.info("══ Compte admin créé — username: admin / password: Admin@1234 ══");
            log.info("══ Changez ce mot de passe à la première connexion !          ══");
        }
    }

    // ── Authentification ──────────────────────────────────────────────────

    public AppUser authenticate(String username, String password) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Identifiants incorrects"));

        if (!user.isActive()) {
            throw new RuntimeException("Compte désactivé — contactez l'administrateur");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Identifiants incorrects");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        return user;
    }

    @Transactional
    public void changePassword(String username, String currentPassword,
                               String newPassword) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Mot de passe actuel incorrect");
        }
        if (currentPassword.equals(newPassword)) {
            throw new RuntimeException(
                    "Le nouveau mot de passe doit être différent de l'actuel");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFirstLogin(false);
        userRepository.save(user);
        log.info("Password changé pour : {}", username);
    }

    // ── Gestion utilisateurs (ADMIN) ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AuthDto.UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AuthDto.UserResponse findById(Long id) {
        return toResponse(getUser(id));
    }

    @Transactional
    public AuthDto.UserResponse createUser(AuthDto.CreateUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException(
                    "Username '" + req.getUsername() + "' déjà utilisé");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException(
                    "Email '" + req.getEmail() + "' déjà utilisé");
        }

        AppUser user = AppUser.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .fullName(req.getFullName())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole())
                .firstLogin(true)
                .active(true)
                .build();

        userRepository.save(user);
        log.info("Utilisateur créé : {} ({})", user.getUsername(), user.getRole());
        return toResponse(user);
    }

    @Transactional
    public AuthDto.UserResponse updateUser(Long id, AuthDto.UpdateUserRequest req) {
        AppUser user = getUser(id);

        if (req.getFullName() != null) user.setFullName(req.getFullName());
        if (req.getEmail()    != null) {
            if (!req.getEmail().equals(user.getEmail()) &&
                    userRepository.existsByEmail(req.getEmail())) {
                throw new IllegalArgumentException("Email déjà utilisé");
            }
            user.setEmail(req.getEmail());
        }
        if (req.getRole()   != null) user.setRole(req.getRole());
        if (req.getActive() != null) user.setActive(req.getActive());

        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        AppUser user = getUser(id);
        if (user.getRole() == Role.ADMIN) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == Role.ADMIN && u.isActive())
                    .count();
            if (adminCount <= 1) {
                throw new IllegalStateException(
                        "Impossible de supprimer le dernier administrateur");
            }
        }
        userRepository.delete(user);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private AppUser getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable : id=" + id));
    }

    public AuthDto.UserResponse toResponse(AppUser u) {
        AuthDto.UserResponse dto = new AuthDto.UserResponse();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setEmail(u.getEmail());
        dto.setFullName(u.getFullName());
        dto.setRole(u.getRole());
        dto.setFirstLogin(u.isFirstLogin());
        dto.setActive(u.isActive());
        dto.setCreatedAt(u.getCreatedAt());
        dto.setLastLoginAt(u.getLastLoginAt());
        return dto;
    }
}