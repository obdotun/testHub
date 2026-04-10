package com.testhub.entity;

import com.testhub.dto.LogMessage;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persiste chaque ligne de log du setup venv d'un projet.
 * Permet de consulter les logs après la fin de l'installation
 * — même après rechargement de la page.
 */
@Entity
@Table(name = "setup_log", indexes = {
        @Index(name = "idx_setup_log_project_id", columnList = "project_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SetupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private TestProject project;

    @Column(nullable = false, length = 2000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogMessage.Level level;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }
}