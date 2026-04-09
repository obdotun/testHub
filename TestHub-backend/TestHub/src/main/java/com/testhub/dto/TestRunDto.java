package com.testhub.dto;

import com.testhub.enums.ExecutionMode;
import com.testhub.enums.RunStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

public class TestRunDto {

    /**
     * Requête de lancement envoyée par le frontend.
     *
     * Exemples :
     *  - Suite entière  : mode=SUITE,       target="Tests/"
     *  - Un fichier     : mode=SUITE,       target="Tests/login.robot"
     *  - Un test précis : mode=SINGLE_TEST, target="Tests/login.robot::Login Valide"
     */
    @Data
    public static class LaunchRequest {

        @NotNull(message = "Le projet est obligatoire")
        private Long projectId;

        @NotNull(message = "Le mode est obligatoire")
        private ExecutionMode mode;

        @NotBlank(message = "La cible est obligatoire")
        private String target;

        /** Label affiché dans l'interface — généré automatiquement si absent */
        private String label;

        /** Forcer pabot même si le projet ne l'a pas détecté automatiquement */
        private boolean forcePabot;

        /** Nombre de processus pabot (défaut : 2) */
        private int processes = 2;
    }

    /** Réponse renvoyée au frontend */
    @Data
    public static class Response {
        private Long          id;
        private Long          projectId;
        private String        projectName;
        private ExecutionMode mode;
        private String        target;
        private String        label;
        private RunStatus     status;
        private Integer       passed;
        private Integer       failed;
        private Integer       skipped;
        private Long          durationSeconds;
        private String        reportPath;
        private String        errorMessage;
        private boolean       executedWithPabot;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
    }
}