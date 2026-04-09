package com.testhub.controller;

import com.testhub.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final StorageConfig storageConfig;

    /**
     * GET /api/reports/{runId}/report → report.html
     * Le frontend l'affiche dans un <iframe>.
     */
    @GetMapping("/{runId}/report")
    public ResponseEntity<Resource> getReport(@PathVariable Long runId) {
        return serve(runId, "report.html", MediaType.TEXT_HTML);
    }

    /**
     * GET /api/reports/{runId}/log → log.html (détail complet)
     */
    @GetMapping("/{runId}/log")
    public ResponseEntity<Resource> getLog(@PathVariable Long runId) {
        return serve(runId, "log.html", MediaType.TEXT_HTML);
    }

    /**
     * GET /api/reports/{runId}/output → output.xml (pour outils externes)
     */
    @GetMapping("/{runId}/output")
    public ResponseEntity<Resource> getOutput(@PathVariable Long runId) {
        return serve(runId, "output.xml", MediaType.APPLICATION_XML);
    }

    private ResponseEntity<Resource> serve(Long runId, String filename, MediaType type) {
        Path file = storageConfig.getReportsPath()
                .resolve("run-" + runId)
                .resolve(filename);

        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(type)
                .body(new FileSystemResource(file));
    }
}