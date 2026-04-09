package com.testhub.entity;

import com.testhub.enums.ExecutionMode;
import com.testhub.enums.RunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Représente une exécution lancée par l'utilisateur.
 *
 * Mode SUITE      → robot/pabot {testsDir}/           (toute la suite)
 * Mode SUITE      → robot/pabot Tests/login.robot     (un fichier)
 * Mode SINGLE_TEST → robot --test "Mon Test" Tests/login.robot
 */
@Entity
@Table(name = "test_run")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private TestProject project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionMode mode;

    /**
     * Ce qui est exécuté (chemin relatif depuis la racine du projet) :
     * - SUITE       → "Tests/"  ou  "Tests/login.robot"
     * - SINGLE_TEST → "Tests/login.robot::Mon Test Case"
     */
    @Column(nullable = false)
    private String target;

    /** Label affiché dans l'interface */
    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RunStatus status = RunStatus.PENDING;

    /** Nombre de tests passés */
    private Integer passed;

    /** Nombre de tests échoués */
    private Integer failed;

    /** Nombre de tests ignorés (skipped) */
    private Integer skipped;

    /** Durée totale en secondes */
    private Long durationSeconds;

    /**
     * Chemin relatif du dossier de rapport généré.
     * Exemple : "run-42"  → reports-dir/run-42/report.html
     */
    private String reportPath;

    /** Message d'erreur système si status = ERROR */
    @Column(length = 2000)
    private String errorMessage;

    /** true si exécuté avec pabot, false si robot */
    private boolean executedWithPabot;

    @Column(nullable = false, updatable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
    }
}