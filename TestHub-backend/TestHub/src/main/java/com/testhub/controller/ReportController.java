package com.testhub.controller;

import com.testhub.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sert les fichiers générés par Robot Framework pour un run donné.
 *
 * Endpoints :
 *   GET /api/reports/{runId}/report  → report.html
 *   GET /api/reports/{runId}/log     → log.html
 *   GET /api/reports/{runId}/output  → output.xml
 *   GET /api/reports/{runId}/{file}  → tout autre fichier (screenshots, assets...)
 *
 * Le token JWT est accepté en header Authorization: Bearer
 * OU en query param ?token= (nécessaire pour les iframes et les images).
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final StorageConfig storageConfig;

    /** report.html */
    @GetMapping("/{runId}/report")
    public ResponseEntity<Resource> getReport(@PathVariable Long runId) throws IOException {
        return serveFile(runId, "report.html");
    }

    /** log.html */
    @GetMapping("/{runId}/log")
    public ResponseEntity<Resource> getLog(@PathVariable Long runId) throws IOException {
        return serveFile(runId, "log.html");
    }

    /** output.xml */
    @GetMapping("/{runId}/output")
    public ResponseEntity<Resource> getOutput(@PathVariable Long runId) throws IOException {
        return serveFile(runId, "output.xml");
    }

    /**
     * Tout autre fichier du dossier run :
     *   - Screenshots : 0-selenium-screenshot-5.png, selenium-screenshot-1.png...
     *   - Assets Robot Framework : robot_framework.js, etc.
     *
     * Appelé automatiquement par le navigateur quand log.html
     * charge des images référencées en chemin relatif.
     */
//    @GetMapping("/{runId}/{filename:.+}")
    @GetMapping("/{runId}/**")
    public ResponseEntity<Resource> getFile(
            @PathVariable Long runId,
            @PathVariable String filename) throws IOException {
        return serveFile(runId, filename);
    }

    // ── Privé ────────────────────────────────────────────────────────────

    private ResponseEntity<Resource> serveFile(Long runId, String filename)
            throws IOException {

        Path reportDir = storageConfig.getReportsPath()
                .resolve("run-" + runId)
                .toAbsolutePath()
                .normalize();

        Path filePath = reportDir.resolve(filename).normalize();

        // Sécurité — empêcher path traversal (../../etc/passwd)
        if (!filePath.startsWith(reportDir)) {
            log.warn("Tentative de path traversal : {}", filename);
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(filePath)) {
            log.debug("Fichier rapport introuvable : {}", filePath);
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);

        // Détecter le type MIME automatiquement
        String mimeType = Files.probeContentType(filePath);
        if (mimeType == null) {
            // Fallback selon l'extension
            String name = filename.toLowerCase();
            if (name.endsWith(".html"))  mimeType = "text/html";
            else if (name.endsWith(".xml"))   mimeType = "application/xml";
            else if (name.endsWith(".png"))   mimeType = "image/png";
            else if (name.endsWith(".jpg") ||
                    name.endsWith(".jpeg"))  mimeType = "image/jpeg";
            else if (name.endsWith(".js"))    mimeType = "application/javascript";
            else if (name.endsWith(".css"))   mimeType = "text/css";
            else                              mimeType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .body(resource);
    }
}