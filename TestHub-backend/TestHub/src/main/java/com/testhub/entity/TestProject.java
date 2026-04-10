package com.testhub.entity;

import com.testhub.enums.ProjectSource;
import com.testhub.enums.VenvStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Un projet peut être créé de deux façons :
 *   - ZIP uploadé        (source = ZIP)
 *   - Clone Bitbucket    (source = GIT)
 *
 * Structure attendue :
 *   Tests/           → fichiers .robot
 *   Resources/       → keywords partagés
 *   Variables/       → fichiers de variables
 *   Libraries/       → librairies custom
 *   requirements.txt
 *   .env             → variables d'environnement (optionnel)
 */
@Entity
@Table(name = "test_project")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TestProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 500)
    private String description;

    /** Chemin absolu du dossier du projet sur le serveur */
    @Column(nullable = false)
    private String storagePath;

    // ── Source du projet ─────────────────────────────────────────────────────

    /** ZIP ou GIT */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ProjectSource source = ProjectSource.ZIP;

    /** Nom du ZIP original (si source = ZIP) */
    private String originalZipName;

    /** URL du repo Bitbucket (si source = GIT) */
    @Column(length = 1000)
    private String repositoryUrl;

    /** Branche clonée (si source = GIT) */
    @Builder.Default
    private String branch = "main";

    /** Dernier commit cloné */
    private String lastCommit;

    /** Date du dernier pull */
    private LocalDateTime lastPulledAt;

    // ── Venv ─────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VenvStatus venvStatus = VenvStatus.NONE;

    private String venvPath;

    private LocalDateTime venvCreatedAt;

    @Column(length = 2000)
    private String venvError;

    // ── Exécution ─────────────────────────────────────────────────────────────

    @Builder.Default
    private boolean usesPabot = false;

    @Column(nullable = false)
    @Builder.Default
    private String testsDir = "Tests";

    // ── Métadonnées ───────────────────────────────────────────────────────────

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ── Relations ─────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RobotFile> robotFiles = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TestRun> runs = new ArrayList<>();

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