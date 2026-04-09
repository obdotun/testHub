package com.testhub.service;

import com.testhub.config.StorageConfig;
import com.testhub.dto.RobotFileDto;
import com.testhub.dto.TestProjectDto;
import com.testhub.entity.TestProject;
import com.testhub.enums.RunStatus;
import com.testhub.enums.VenvStatus;
import com.testhub.repository.RobotFileRepository;
import com.testhub.repository.TestCaseRepository;
import com.testhub.repository.TestProjectRepository;
import com.testhub.repository.TestRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestProjectService {

    private final TestProjectRepository projectRepository;
    private final TestRunRepository     runRepository;
    private final RobotFileRepository   robotFileRepository;
    private final TestCaseRepository    testCaseRepository;
    private final StorageConfig         storageConfig;
    private final ZipExtractorService   zipExtractor;
    private final ProjectIndexerService indexer;
    private final VenvSetupService      venvSetup;

    /**
     * Crée un projet à partir d'un ZIP uploadé.
     * Enchaîne : extraction → indexation → setup venv (async).
     */
    @Transactional
    public TestProjectDto.Response createFromZip(
            TestProjectDto.CreateRequest req, MultipartFile zipFile) throws IOException {

        if (projectRepository.existsByName(req.getName())) {
            throw new IllegalArgumentException(
                    "Un projet nommé '" + req.getName() + "' existe déjà.");
        }
        if (!isZip(zipFile)) {
            throw new IllegalArgumentException(
                    "Le fichier doit être un ZIP valide.");
        }

        // Créer le dossier de stockage
        String safeName = req.getName().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        Path projectPath = storageConfig.getProjectsPath().resolve(safeName);
        Files.createDirectories(projectPath);

        // Extraire le ZIP (sans venv/, Results/, etc.)
        int extracted = zipExtractor.extract(zipFile.getInputStream(), projectPath);
        log.info("Projet '{}' : {} fichiers extraits", req.getName(), extracted);

        // Détecter si le ZIP avait un dossier racine (ex: eforex_im_gc_qa/)
        // et ajuster le storagePath si nécessaire
        Path effectivePath = detectRootDir(projectPath);

        // Persister le projet
        TestProject project = TestProject.builder()
                .name(req.getName())
                .description(req.getDescription())
                .storagePath(effectivePath.toString())
                .originalZipName(zipFile.getOriginalFilename())
                .testsDir(req.getTestsDir() != null ? req.getTestsDir() : "Tests")
                .venvStatus(VenvStatus.NONE)
                .build();

        project = projectRepository.save(project);

        // Indexer les fichiers .robot en base
        indexer.indexProject(project);

        // ⚠️ NE PAS appeler setupAsync ici — la transaction n'est pas encore
        // committée. Le thread @Async ne trouverait pas le projet en base (404).
        // setupAsync est déclenché par le controller APRÈS le commit.

        return toResponse(project);
    }

    /**
     * Déclenche l'installation du venv après le commit de la transaction.
     * Appelé par le controller juste après createFromZip().
     */
    public void triggerVenvSetup(Long projectId) {
        venvSetup.setupAsync(projectId);
    }

    /**
     * Réindexe les fichiers .robot sans re-uploader le ZIP.
     * Utile si le contenu a été modifié manuellement.
     */
    @Transactional
    public TestProjectDto.Response reindex(Long projectId) throws IOException {
        TestProject project = getProject(projectId);
        indexer.indexProject(project);
        return toResponse(project);
    }

    /**
     * Relance l'installation du venv (requirements.txt mis à jour).
     */
    public void reinstallVenv(Long projectId) {
        getProject(projectId); // vérification existence
        venvSetup.reinstallAsync(projectId);
    }

    @Transactional(readOnly = true)
    public List<TestProjectDto.Response> findAll() {
        return projectRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TestProjectDto.Response findById(Long id) {
        return toResponse(getProject(id));
    }

    /**
     * Retourne l'arborescence des fichiers .robot d'un projet.
     */
    @Transactional(readOnly = true)
    public RobotFileDto.ProjectTree getProjectTree(Long projectId) {
        TestProject project = getProject(projectId);

        List<RobotFileDto.RobotFileItem> files = robotFileRepository
                .findByProjectIdOrderByRelativePath(projectId)
                .stream()
                .map(rf -> RobotFileDto.RobotFileItem.builder()
                        .id(rf.getId())
                        .relativePath(rf.getRelativePath())
                        .suiteName(rf.getSuiteName())
                        .sizeBytes(rf.getSizeBytes())
                        .testCases(rf.getTestCases().stream()
                                .map(tc -> RobotFileDto.TestCaseItem.builder()
                                        .id(tc.getId())
                                        .name(tc.getName())
                                        .position(tc.getPosition())
                                        .tags(tc.getTags())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        int totalTc = files.stream().mapToInt(f -> f.getTestCases().size()).sum();

        return RobotFileDto.ProjectTree.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .testsDir(project.getTestsDir())
                .files(files)
                .totalFiles(files.size())
                .totalTestCases(totalTc)
                .build();
    }

    @Transactional
    public void deleteProject(Long id) {
        TestProject project = getProject(id);
        projectRepository.delete(project);
        // Optionnel : supprimer le dossier physique
        try {
            deleteDir(Path.of(project.getStoragePath()));
        } catch (IOException e) {
            log.warn("Impossible de supprimer le dossier du projet : {}", e.getMessage());
        }
    }

    // ── Privé ────────────────────────────────────────────────────────────────

    /**
     * Certains ZIPs contiennent un dossier racine unique (ex: eforex_im_gc_qa/).
     * Si c'est le cas, on descend d'un niveau pour que storagePath pointe
     * directement vers ce dossier.
     */
    private Path detectRootDir(Path extractedPath) throws IOException {
        try (var stream = Files.list(extractedPath)) {
            List<Path> children = stream.collect(Collectors.toList());
            if (children.size() == 1 && Files.isDirectory(children.get(0))) {
                Path candidate = children.get(0);
                // Vérifier que c'est bien la racine du projet (contient Tests/ ou requirements.txt)
                boolean hasTests = Files.exists(candidate.resolve("Tests"))
                        || Files.exists(candidate.resolve("requirements.txt"))
                        || Files.exists(candidate.resolve(".env"));
                if (hasTests) {
                    log.info("Dossier racine détecté dans le ZIP : {}", candidate.getFileName());
                    return candidate;
                }
            }
        }
        return extractedPath;
    }

    private boolean isZip(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase().endsWith(".zip")) return true;
        String contentType = file.getContentType();
        return contentType != null && (
                contentType.equals("application/zip") ||
                        contentType.equals("application/x-zip-compressed") ||
                        contentType.equals("application/octet-stream"));
    }

    private TestProject getProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projet introuvable : id=" + id));
    }

    private void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
    }

    // ── Mapper ───────────────────────────────────────────────────────────────

    private TestProjectDto.Response toResponse(TestProject p) {
        TestProjectDto.Response dto = new TestProjectDto.Response();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setDescription(p.getDescription());
        dto.setTestsDir(p.getTestsDir());
        dto.setOriginalZipName(p.getOriginalZipName());
        dto.setVenvStatus(p.getVenvStatus());
        dto.setVenvError(p.getVenvError());
        dto.setUsesPabot(p.isUsesPabot());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        dto.setVenvCreatedAt(p.getVenvCreatedAt());
        dto.setTotalFiles((int) robotFileRepository.count());
        dto.setTotalTestCases((int) testCaseRepository.countByProjectId(p.getId()));
        dto.setTotalRuns(runRepository.countByProjectIdAndStatus(p.getId(), RunStatus.PASSED)
                + runRepository.countByProjectIdAndStatus(p.getId(), RunStatus.FAILED)
                + runRepository.countByProjectIdAndStatus(p.getId(), RunStatus.ERROR));
        dto.setPassedRuns(runRepository.countByProjectIdAndStatus(p.getId(), RunStatus.PASSED));
        dto.setFailedRuns(runRepository.countByProjectIdAndStatus(p.getId(), RunStatus.FAILED));
        return dto;
    }
}