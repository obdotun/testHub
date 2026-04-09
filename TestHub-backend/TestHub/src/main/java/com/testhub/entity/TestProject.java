package com.testhub.entity;

import com.testhub.enums.VenvStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Un projet correspond à un ZIP uploadé, extrait dans storage/projects/{id}/.
 * Chaque projet possède son propre venv Python isolé.
 *
 * Structure attendue dans le ZIP :
 *   Tests/          → fichiers .robot (test cases)
 *   Resources/      → keywords et resources partagés
 *   Variables/      → fichiers de variables
 *   Libraries/      → librairies custom
 *   requirements.txt
 *   .env            → variables d'environnement (optionnel)
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

    /** Chemin absolu du dossier extrait sur le serveur */
    @Column(nullable = false)
    private String storagePath;

    /** Nom du ZIP original uploadé */
    private String originalZipName;

    // ── Venv ────────────────────────────────────────────────────────────────

    /** État du venv de ce projet */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VenvStatus venvStatus = VenvStatus.NONE;

    /** Chemin absolu du venv : {storagePath}/venv */
    private String venvPath;

    /** Date de création/dernière installation du venv */
    private LocalDateTime venvCreatedAt;

    /** Dernière erreur lors du setup venv */
    @Column(length = 2000)
    private String venvError;

    // ── Exécution ───────────────────────────────────────────────────────────

    /**
     * Si true → utilise pabot (parallèle) au lieu de robot.
     * Détecté automatiquement si "robotframework-pabot" est dans requirements.txt.
     */
    @Builder.Default
    private boolean usesPabot = false;

    /**
     * Dossier contenant les suites à exécuter.
     * Par convention : "Tests" — peut être surchargé.
     */
    @Column(nullable = false)
    @Builder.Default
    private String testsDir = "Tests";

    // ── Métadonnées ─────────────────────────────────────────────────────────

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ── Relations ───────────────────────────────────────────────────────────

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