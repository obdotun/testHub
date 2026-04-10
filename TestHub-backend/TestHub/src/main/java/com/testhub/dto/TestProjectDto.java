package com.testhub.dto;

import com.testhub.enums.ProjectSource;
import com.testhub.enums.VenvStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

public class TestProjectDto {

    // ── Requête création via ZIP ──────────────────────────────────────────────
    @Data
    public static class CreateZipRequest {
        @NotBlank(message = "Le nom est obligatoire")
        private String name;
        private String description;
        private String testsDir = "Tests";
    }

    // ── Requête création via Bitbucket ────────────────────────────────────────
    @Data
    public static class CreateGitRequest {
        @NotBlank(message = "Le nom est obligatoire")
        private String name;

        private String description;

        @NotBlank(message = "L'URL du repo est obligatoire")
        private String repositoryUrl;

        /** Branche à cloner (défaut : main) */
        private String branch = "main";

        private String testsDir = "Tests";

        /**
         * Identifiants Bitbucket pour les repos privés.
         * Format HTTPS : https://username:appPassword@bitbucket.org/org/repo.git
         * Si fournis, ils sont injectés dans l'URL avant le clone.
         */
        private String username;
        private String appPassword;
    }

    // ── Réponse ───────────────────────────────────────────────────────────────
    @Data
    public static class Response {
        private Long          id;
        private String        name;
        private String        description;
        private String        testsDir;
        private ProjectSource source;
        private String        originalZipName;
        private String        repositoryUrl;
        private String        branch;
        private String        lastCommit;
        private LocalDateTime lastPulledAt;
        private VenvStatus    venvStatus;
        private String        venvError;
        private boolean       usesPabot;
        private int           totalFiles;
        private int           totalTestCases;
        private long          totalRuns;
        private long          passedRuns;
        private long          failedRuns;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime venvCreatedAt;
    }
}