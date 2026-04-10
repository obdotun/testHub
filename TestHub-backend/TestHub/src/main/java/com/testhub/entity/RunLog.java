package com.testhub.entity;

import com.testhub.dto.LogMessage;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persiste chaque ligne de log d'un run.
 * Permet de recharger l'historique des logs même après la fin du run.
 */
@Entity
@Table(name = "run_log", indexes = {
        @Index(name = "idx_run_log_run_id", columnList = "run_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private TestRun run;

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