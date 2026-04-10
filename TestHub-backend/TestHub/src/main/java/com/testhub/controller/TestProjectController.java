package com.testhub.controller;

import com.testhub.dto.LogMessage;
import com.testhub.dto.RobotFileDto;
import com.testhub.dto.TestProjectDto;
import com.testhub.service.GitService;
import com.testhub.service.TestProjectService;
import com.testhub.repository.SetupLogRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class TestProjectController {

    private final TestProjectService projectService;
    private final GitService gitService;
    private final SetupLogRepository setupLogRepository;

    /** GET /api/projects */
    @GetMapping
    public ResponseEntity<List<TestProjectDto.Response>> findAll() {
        return ResponseEntity.ok(projectService.findAll());
    }

    /** GET /api/projects/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<TestProjectDto.Response> findById(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.findById(id));
    }

    /** GET /api/projects/{id}/files */
    @GetMapping("/{id}/files")
    public ResponseEntity<RobotFileDto.ProjectTree> getFiles(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectTree(id));
    }

    /**
     * POST /api/projects/zip  (multipart/form-data)
     * Crée un projet depuis un ZIP uploadé.
     */
    @PostMapping(value = "/zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TestProjectDto.Response> createFromZip(
            @RequestParam("name")                        String name,
            @RequestParam(value = "description",
                    required = false)               String description,
            @RequestParam(value = "testsDir",
                    defaultValue = "Tests")         String testsDir,
            @RequestParam("file")                        MultipartFile zipFile)
            throws IOException {

        TestProjectDto.CreateZipRequest req = new TestProjectDto.CreateZipRequest();
        req.setName(name);
        req.setDescription(description);
        req.setTestsDir(testsDir);

        // 1. Créer le projet (transaction committée)
        TestProjectDto.Response response = projectService.createFromZip(req, zipFile);

        // 2. Lancer le venv APRÈS le commit ✅
        projectService.triggerVenvSetup(response.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/projects/git  (application/json)
     * Crée un projet depuis un repo Bitbucket privé.
     *
     * Body :
     * {
     *   "name": "eforex",
     *   "repositoryUrl": "https://bitbucket.org/webbfontaine/eforex_im_gc_qa.git",
     *   "branch": "main",
     *   "testsDir": "Tests",
     *   "username": "mon_username",
     *   "appPassword": "mon_app_password"
     * }
     */
    @PostMapping("/git")
    public ResponseEntity<TestProjectDto.Response> createFromGit(
            @Valid @RequestBody TestProjectDto.CreateGitRequest req)
            throws IOException {

        // 1. Persister le projet (transaction committée)
        TestProjectDto.Response response = projectService.createFromGit(req);

        // 2. Lancer clone + venv APRÈS le commit ✅
        //    Les credentials ne sont pas stockés en base
        projectService.triggerGitCloneAndSetup(
                response.getId(),
                req.getUsername(),
                req.getAppPassword());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/projects/{id}/pull
     * Met à jour un projet Git (git pull + réindexation + réinstallation venv).
     *
     * Body :
     * {
     *   "username": "mon_username",
     *   "appPassword": "mon_app_password"
     * }
     */
    @PostMapping("/{id}/pull")
    public ResponseEntity<Map<String, String>> pull(
            @PathVariable Long id,
            @RequestBody Map<String, String> credentials) {

        projectService.pullProject(
                id,
                credentials.get("username"),
                credentials.get("appPassword"));

        return ResponseEntity.accepted().body(Map.of(
                "message", "Pull lancé — suivez /topic/projects/" + id + "/setup"));
    }

    /**
     * GET /api/projects/{id}/setup-logs
     * Retourne les logs persistés du dernier setup venv.
     * Appelé au montage de l'onglet "Setup venv" pour afficher
     * les logs même après rechargement de la page.
     */
    @GetMapping("/{id}/setup-logs")
    public ResponseEntity<List<LogMessage>> getSetupLogs(@PathVariable Long id) {
        List<LogMessage> logs = setupLogRepository.findByProjectIdOrderByIdAsc(id)
                .stream()
                .map(l -> LogMessage.builder()
                        .sourceId(id)
                        .text(l.getText())
                        .level(l.getLevel())
                        .timestamp(l.getTimestamp())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(logs);
    }

    /**
     * POST /api/projects/git/branches
     * Retourne la liste des branches d'un repo Bitbucket.
     * Utilise git ls-remote — ne clone pas le repo.
     *
     * Body :
     * {
     *   "repositoryUrl": "https://bitbucket.org/org/repo.git",
     *   "username": "mon_username",
     *   "appPassword": "mon_app_password"
     * }
     *
     * Réponse :
     * ["main", "develop", "TRN", "UAT", "PREPROD"]
     */
    @PostMapping("/git/branches")
    public ResponseEntity<List<String>> listBranches(
            @RequestBody Map<String, String> payload) {
        try {
            List<String> branches = gitService.listBranches(
                    payload.get("repositoryUrl"),
                    payload.get("username"),
                    payload.get("appPassword"));
            return ResponseEntity.ok(branches);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(List.of("Erreur : " + e.getMessage()));
        }
    }

    /** POST /api/projects/{id}/reindex */
    @PostMapping("/{id}/reindex")
    public ResponseEntity<TestProjectDto.Response> reindex(@PathVariable Long id)
            throws IOException {
        return ResponseEntity.ok(projectService.reindex(id));
    }

    /** POST /api/projects/{id}/reinstall-venv */
    @PostMapping("/{id}/reinstall-venv")
    public ResponseEntity<Map<String, String>> reinstallVenv(@PathVariable Long id) {
        projectService.reinstallVenv(id);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Réinstallation lancée — suivez /topic/projects/" + id + "/setup"));
    }

    /** DELETE /api/projects/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}