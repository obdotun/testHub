package com.testhub.dto;

import com.testhub.enums.VenvStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

public class TestProjectDto {

    @Data
    public static class CreateRequest {
        @NotBlank(message = "Le nom du projet est obligatoire")
        private String name;
        private String description;

        /**
         * Dossier des suites dans le ZIP (défaut : "Tests").
         * Permet de supporter des structures non standard.
         */
        private String testsDir = "Tests";
    }

    @Data
    public static class Response {
        private Long          id;
        private String        name;
        private String        description;
        private String        testsDir;
        private String        originalZipName;
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