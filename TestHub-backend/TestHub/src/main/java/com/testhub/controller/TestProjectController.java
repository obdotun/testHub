package com.testhub.controller;

import com.testhub.dto.RobotFileDto;
import com.testhub.dto.TestProjectDto;
import com.testhub.service.TestProjectService;
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

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class TestProjectController {

    private final TestProjectService projectService;

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

    /**
     * POST /api/projects  (multipart/form-data)
     * Params :
     *   - name        (texte)
     *   - description (texte, optionnel)
     *   - testsDir    (texte, défaut "Tests")
     *   - file        (ZIP du projet)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TestProjectDto.Response> create(
            @RequestParam("name")                        String name,
            @RequestParam(value = "description",
                    required = false)               String description,
            @RequestParam(value = "testsDir",
                    defaultValue = "Tests")         String testsDir,
            @RequestParam("file")                        MultipartFile zipFile) throws IOException {

        TestProjectDto.CreateRequest req = new TestProjectDto.CreateRequest();
        req.setName(name);
        req.setDescription(description);
        req.setTestsDir(testsDir);

        // 1. Créer le projet — transaction committée à la sortie de createFromZip()
        TestProjectDto.Response response = projectService.createFromZip(req, zipFile);

        // 2. Lancer le venv APRÈS le commit — le projet existe maintenant en base ✅
        projectService.triggerVenvSetup(response.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/projects/{id}/files
     * Retourne l'arborescence des fichiers .robot avec leurs test cases.
     */
    @GetMapping("/{id}/files")
    public ResponseEntity<RobotFileDto.ProjectTree> getFiles(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectTree(id));
    }

    /**
     * POST /api/projects/{id}/reindex
     * Réindexe les fichiers .robot sans re-uploader le ZIP.
     */
    @PostMapping("/{id}/reindex")
    public ResponseEntity<TestProjectDto.Response> reindex(@PathVariable Long id) throws IOException {
        return ResponseEntity.ok(projectService.reindex(id));
    }

    /**
     * POST /api/projects/{id}/reinstall-venv
     * Supprime et recrée le venv (si requirements.txt a changé).
     */
    @PostMapping("/{id}/reinstall-venv")
    public ResponseEntity<Map<String, String>> reinstallVenv(@PathVariable Long id) {
        projectService.reinstallVenv(id);
        return ResponseEntity.accepted()
                .body(Map.of("message",
                        "Réinstallation lancée — suivez /topic/projects/" + id + "/setup"));
    }

    /** DELETE /api/projects/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}