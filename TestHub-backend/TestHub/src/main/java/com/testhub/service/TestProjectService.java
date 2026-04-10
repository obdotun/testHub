package com.testhub.service;

import com.testhub.config.StorageConfig;
import com.testhub.dto.RobotFileDto;
import com.testhub.dto.TestProjectDto;
import com.testhub.entity.TestProject;
import com.testhub.enums.ProjectSource;
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
import java.time.LocalDateTime;
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
    private final GitService            gitService;

    // ── Création via ZIP ─────────────────────────────────────────────────────

    @Transactional
    public TestProjectDto.Response createFromZip(
            TestProjectDto.CreateZipRequest req, MultipartFile zipFile) throws IOException {

        validateName(req.getName());
        if (!isZip(zipFile)) {
            throw new IllegalArgumentException("Le fichier doit être un ZIP valide.");
        }

        Path projectPath = initProjectDir(req.getName());
        int extracted = zipExtractor.extract(zipFile.getInputStream(), projectPath);
        log.info("Projet '{}' : {} fichiers extraits depuis ZIP", req.getName(), extracted);

        Path effectivePath = detectRootDir(projectPath);

        TestProject project = TestProject.builder()
                .name(req.getName())
                .description(req.getDescription())
                .storagePath(effectivePath.toString())
                .originalZipName(zipFile.getOriginalFilename())
                .testsDir(req.getTestsDir() != null ? req.getTestsDir() : "Tests")
                .source(ProjectSource.ZIP)
                .venvStatus(VenvStatus.NONE)
                .build();

        project = projectRepository.save(project);
        indexer.indexProject(project);
        return toResponse(project);
    }

    // ── Création via Bitbucket ────────────────────────────────────────────────

    @Transactional
    public TestProjectDto.Response createFromGit(
            TestProjectDto.CreateGitRequest req) throws IOException {

        validateName(req.getName());

        Path projectPath = initProjectDir(req.getName());

        // Masquer les credentials dans l'URL stockée en base
        String publicUrl = req.getRepositoryUrl();

        TestProject project = TestProject.builder()
                .name(req.getName())
                .description(req.getDescription())
                .storagePath(projectPath.toString())
                .repositoryUrl(publicUrl)
                .branch(req.getBranch() != null ? req.getBranch() : "main")
                .testsDir(req.getTestsDir() != null ? req.getTestsDir() : "Tests")
                .source(ProjectSource.GIT)
                .venvStatus(VenvStatus.NONE)
                .build();

        project = projectRepository.save(project);

        // Le clone est lancé de façon asynchrone pour ne pas bloquer la réponse HTTP
        // Les logs arrivent via WebSocket /topic/projects/{id}/setup
        final Long projectId = project.getId();
        final String username    = req.getUsername();
        final String appPassword = req.getAppPassword();
        final String branch      = project.getBranch();

        // Persister avant de lancer le clone async
        return toResponse(project);
    }

    /**
     * Déclenche le clone Git + venv en arrière-plan.
     * Appelé par le controller APRÈS le commit de la transaction.
     */
    public void triggerGitCloneAndSetup(Long projectId, String username,
                                        String appPassword) {
        gitCloneAsync(projectId, username, appPassword);
    }

    /**
     * Déclenche l'installation du venv en arrière-plan.
     * Appelé par le controller APRÈS le commit de la transaction (ZIP).
     */
    public void triggerVenvSetup(Long projectId) {
        venvSetup.setupAsync(projectId);
    }

    // ── Pull (mise à jour depuis Bitbucket) ───────────────────────────────────

    public void pullProject(Long projectId, String username, String appPassword) {
        gitPullAsync(projectId, username, appPassword);
    }

    // ── Async Git ─────────────────────────────────────────────────────────────

    private void gitCloneAsync(Long projectId, String username, String appPassword) {
        // Exécution dans un thread séparé
        new Thread(() -> {
            TestProject project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Projet introuvable : " + projectId));

            String topic = "/topic/projects/" + projectId + "/setup";
            Path projectPath = Path.of(project.getStoragePath()).toAbsolutePath().normalize();

            try {
                // ── Clone ──────────────────────────────────────────────────
                gitService.clone(
                        project.getRepositoryUrl(),
                        username, appPassword,
                        project.getBranch(),
                        projectPath,
                        topic, projectId);

                // Détecter dossier racine dans le clone
                Path effectivePath = detectRootDir(projectPath);
                if (!effectivePath.equals(projectPath)) {
                    project.setStoragePath(effectivePath.toString());
                }

                // Récupérer le hash du dernier commit
                String commit = gitService.getLastCommit(effectivePath);
                project.setLastCommit(commit);
                project.setLastPulledAt(LocalDateTime.now());
                projectRepository.save(project);

                // ── Indexation ─────────────────────────────────────────────
                indexer.indexProject(project);

                // ── Setup venv ─────────────────────────────────────────────
                venvSetup.setupAsync(projectId);

            } catch (Exception e) {
                log.error("Erreur clone projet {} : {}", projectId, e.getMessage());
                project.setVenvStatus(VenvStatus.ERROR);
                project.setVenvError("Erreur clone : " + e.getMessage());
                projectRepository.save(project);
            }
        }, "git-clone-" + projectId).start();
    }

    private void gitPullAsync(Long projectId, String username, String appPassword) {
        new Thread(() -> {
            TestProject project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Projet introuvable : " + projectId));

            String topic = "/topic/projects/" + projectId + "/setup";
            Path projectPath = Path.of(project.getStoragePath()).toAbsolutePath().normalize();

            try {
                project.setVenvStatus(VenvStatus.NONE);
                projectRepository.save(project);

                gitService.pull(
                        projectPath,
                        username, appPassword,
                        project.getBranch(),
                        project.getRepositoryUrl(),
                        topic, projectId);

                String commit = gitService.getLastCommit(projectPath);
                project.setLastCommit(commit);
                project.setLastPulledAt(LocalDateTime.now());
                projectRepository.save(project);

                // Réindexer après le pull
                indexer.indexProject(project);

                // Réinstaller le venv (requirements.txt peut avoir changé)
                venvSetup.reinstallAsync(projectId);

            } catch (Exception e) {
                log.error("Erreur pull projet {} : {}", projectId, e.getMessage());
                project.setVenvStatus(VenvStatus.ERROR);
                project.setVenvError("Erreur pull : " + e.getMessage());
                projectRepository.save(project);
            }
        }, "git-pull-" + projectId).start();
    }

    // ── Autres méthodes existantes ─────────────────────────────────────────────

    @Transactional
    public TestProjectDto.Response reindex(Long projectId) throws IOException {
        TestProject project = getProject(projectId);
        indexer.indexProject(project);
        return toResponse(project);
    }

    public void reinstallVenv(Long projectId) {
        getProject(projectId);
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
        try { deleteDir(Path.of(project.getStoragePath())); }
        catch (IOException e) { log.warn("Dossier non supprimé : {}", e.getMessage()); }
    }

    // ── Privé ─────────────────────────────────────────────────────────────────

    private void validateName(String name) {
        if (projectRepository.existsByName(name)) {
            throw new IllegalArgumentException(
                    "Un projet nommé '" + name + "' existe déjà.");
        }
    }

    private Path initProjectDir(String name) throws IOException {
        String safeName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        Path path = storageConfig.getProjectsPath().resolve(safeName);
        Files.createDirectories(path);
        return path;
    }

    private Path detectRootDir(Path extractedPath) throws IOException {
        try (var stream = Files.list(extractedPath)) {
            List<Path> children = stream.collect(Collectors.toList());
            if (children.size() == 1 && Files.isDirectory(children.get(0))) {
                Path candidate = children.get(0);
                boolean hasMarker = Files.exists(candidate.resolve("Tests"))
                        || Files.exists(candidate.resolve("requirements.txt"))
                        || Files.exists(candidate.resolve(".env"))
                        || Files.exists(candidate.resolve(".git"));
                if (hasMarker) return candidate;
            }
        }
        return extractedPath;
    }

    private boolean isZip(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase().endsWith(".zip")) return true;
        String ct = file.getContentType();
        return ct != null && (ct.equals("application/zip") ||
                ct.equals("application/x-zip-compressed") ||
                ct.equals("application/octet-stream"));
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

    private TestProjectDto.Response toResponse(TestProject p) {
        TestProjectDto.Response dto = new TestProjectDto.Response();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setDescription(p.getDescription());
        dto.setTestsDir(p.getTestsDir());
        dto.setSource(p.getSource());
        dto.setOriginalZipName(p.getOriginalZipName());
        dto.setRepositoryUrl(p.getRepositoryUrl());
        dto.setBranch(p.getBranch());
        dto.setLastCommit(p.getLastCommit());
        dto.setLastPulledAt(p.getLastPulledAt());
        dto.setVenvStatus(p.getVenvStatus());
        dto.setVenvError(p.getVenvError());
        dto.setUsesPabot(p.isUsesPabot());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        dto.setVenvCreatedAt(p.getVenvCreatedAt());
        dto.setTotalFiles(robotFileRepository.findByProjectIdOrderByRelativePath(p.getId()).size());
        dto.setTotalTestCases((int) testCaseRepository.countByProjectId(p.getId()));
        dto.setPassedRuns(runRepository.countByProjectIdAndStatus(p.getId(), RunStatus.PASSED));
        dto.setFailedRuns(runRepository.countByProjectIdAndStatus(p.getId(), RunStatus.FAILED));
        dto.setTotalRuns(dto.getPassedRuns() + dto.getFailedRuns() +
                runRepository.countByProjectIdAndStatus(p.getId(), RunStatus.ERROR));
        return dto;
    }
}