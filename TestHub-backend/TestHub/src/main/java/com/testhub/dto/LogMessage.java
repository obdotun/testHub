package com.testhub.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Message streamé en WebSocket vers le frontend.
 *
 * Deux canaux utilisent ce DTO :
 *  - Setup venv  : /topic/projects/{projectId}/setup
 *  - Exécution   : /topic/runs/{runId}/logs
 */
@Data
@Builder
public class LogMessage {

    public enum Level {
        INFO,    // log standard
        WARN,    // avertissement
        ERROR,   // erreur
        SYSTEM,  // message système (début/fin, séparateurs)
        SUCCESS  // succès (venv prêt, test passé)
    }

    private Long    sourceId;   // projectId ou runId selon le canal
    private String  text;
    private Level   level;
    private LocalDateTime timestamp;

    public static LogMessage info(Long id, String text) {
        return build(id, text, Level.INFO);
    }

    public static LogMessage warn(Long id, String text) {
        return build(id, text, Level.WARN);
    }

    public static LogMessage error(Long id, String text) {
        return build(id, text, Level.ERROR);
    }

    public static LogMessage system(Long id, String text) {
        return build(id, text, Level.SYSTEM);
    }

    public static LogMessage success(Long id, String text) {
        return build(id, text, Level.SUCCESS);
    }

    private static LogMessage build(Long id, String text, Level level) {
        return LogMessage.builder()
                .sourceId(id)
                .text(text)
                .level(level)
                .timestamp(LocalDateTime.now())
                .build();
    }
}