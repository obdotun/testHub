package com.testhub.service;

import com.testhub.config.StorageConfig;
import com.testhub.dto.LogMessage;
import com.testhub.dto.TestRunDto;
import com.testhub.entity.RunLog;
import com.testhub.entity.TestProject;
import com.testhub.entity.TestRun;
import com.testhub.enums.ExecutionMode;
import com.testhub.enums.RunStatus;
import com.testhub.enums.VenvStatus;
import com.testhub.repository.RunLogRepository;
import com.testhub.repository.TestProjectRepository;
import com.testhub.repository.TestRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final TestRunRepository     runRepository;
    private final TestProjectRepository projectRepository;
    private final RunLogRepository      runLogRepository;  // ← persistance logs
    private final StorageConfig         storageConfig;
    private final SimpMessagingTemplate messaging;
    private final DotEnvLoader          dotEnvLoader;
    private final OutputXmlParser       outputXmlParser;

    // ── Lancement ────────────────────────────────────────────────────────────

    public TestRunDto.Response launchRun(TestRunDto.LaunchRequest req) {
        TestProject project = projectRepository.findById(req.getProjectId())
                .orElseThrow(() -> new RuntimeException(
                        "Projet introuvable : id=" + req.getProjectId()));

        if (project.getVenvStatus() != VenvStatus.READY) {
            throw new IllegalStateException(
                    "Le venv du projet n'est pas prêt (statut : " +
                            project.getVenvStatus() + ").");
        }

        boolean usePabot = req.isForcePabot() || project.isUsesPabot();

        TestRun run = TestRun.builder()
                .project(project)
                .mode(req.getMode())
                .target(req.getTarget())
                .label(buildLabel(req))
                .status(RunStatus.PENDING)
                .executedWithPabot(usePabot)
                .build();

        run = runRepository.save(run);
        executeAsync(run.getId(), req.getProcesses());
        return toResponse(run);
    }

    // ── Exécution asynchrone ─────────────────────────────────────────────────

    @Async
    public void executeAsync(Long runId, int processes) {
        TestRun run = runRepository.findById(runId)
                .orElseThrow(() -> new RuntimeException("Run introuvable : " + runId));

        String topic = "/topic/runs/" + runId + "/logs";

        run.setStatus(RunStatus.RUNNING);
        runRepository.save(run);

        TestProject project = run.getProject();

        // Chemins absolus normalisés (cross-platform Windows + Linux/Docker)
        Path projectPath = Path.of(project.getStoragePath()).toAbsolutePath().normalize();
        Path venvPath    = Path.of(project.getVenvPath()).toAbsolutePath().normalize();
        Path reportDir   = storageConfig.getReportsPath()
                .resolve("run-" + runId)
                .toAbsolutePath().normalize();

        send(run, topic, LogMessage.system(runId, "══════════════════════════════════════"));
        send(run, topic, LogMessage.system(runId, "  Exécution #" + runId + " — " + run.getLabel()));
        send(run, topic, LogMessage.system(runId, "══════════════════════════════════════"));

        // Vérification dossier projet
        if (!Files.exists(projectPath) || !Files.isDirectory(projectPath)) {
            String msg = "Dossier projet introuvable : " + projectPath;
            send(run, topic, LogMessage.error(runId, msg));
            errorFinalize(run, msg);
            return;
        }

        // Vérification venv
        if (!Files.exists(venvPath) || !Files.isDirectory(venvPath)) {
            String msg = "Venv introuvable : " + venvPath + " — relancez l'installation";
            send(run, topic, LogMessage.error(runId, msg));
            errorFinalize(run, msg);
            return;
        }

        try {
            Files.createDirectories(reportDir);

            List<String> command = buildCommand(run, venvPath, projectPath, reportDir, processes);
            send(run, topic, LogMessage.system(runId,
                    "Commande : " + String.join(" ", command)));

            Map<String, String> envVars = dotEnvLoader.load(projectPath);
            if (!envVars.isEmpty()) {
                send(run, topic, LogMessage.info(runId,
                        "Variables .env chargées : " + envVars.size()));
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);

            // Configure venv dans l'environnement (cross-platform)
            setupEnvironment(pb, venvPath, envVars);

            LocalDateTime start   = LocalDateTime.now();
            Process       process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    send(run, topic, categorize(runId, line));
                }
            }

            boolean finished = process.waitFor(
                    storageConfig.getExecutionTimeoutSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                finalize(run, RunStatus.ERROR, start, reportDir, 0, 0, 0,
                        "Timeout dépassé (" + storageConfig.getExecutionTimeoutSeconds() + "s)");
                send(run, topic, LogMessage.error(runId, "✘ Timeout dépassé"));
                return;
            }

            OutputXmlParser.Result result = outputXmlParser.parse(reportDir);
            RunStatus status = result.allPassed() ? RunStatus.PASSED : RunStatus.FAILED;
            finalize(run, status, start, reportDir,
                    result.passed(), result.failed(), result.skipped(), null);

            String summary = String.format(
                    "Résultat : %d passés | %d échoués | %d ignorés",
                    result.passed(), result.failed(), result.skipped());
            send(run, topic, status == RunStatus.PASSED
                    ? LogMessage.success(runId, "✔ " + summary)
                    : LogMessage.error(runId, "✘ " + summary));

        } catch (Exception e) {
            log.error("Erreur lors du run {}", runId, e);
            send(run, topic, LogMessage.error(runId, "Erreur système : " + e.getMessage()));
            errorFinalize(run, e.getMessage());
        }

        send(run, topic, LogMessage.system(runId,
                "══ Fin exécution : " + run.getStatus() + " ══"));
    }

    // ── Logs persistés ───────────────────────────────────────────────────────

    /**
     * Retourne les logs persistés d'un run.
     * Appelé par le frontend au montage de RunDetail
     * pour afficher les logs même si le run est déjà terminé.
     */
    public List<LogMessage> getRunLogs(Long runId) {
        return runLogRepository.findByRunIdOrderByIdAsc(runId)
                .stream()
                .map(l -> LogMessage.builder()
                        .sourceId(runId)
                        .text(l.getText())
                        .level(l.getLevel())
                        .timestamp(l.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Envoie un log via WebSocket ET le persiste en base.
     * Garantit que les logs sont disponibles même après navigation.
     */
    private void send(TestRun run, String topic, LogMessage msg) {
        // 1. WebSocket temps réel
        messaging.convertAndSend(topic, msg);

        // 2. Persistance en base
        try {
            runLogRepository.save(RunLog.builder()
                    .run(run)
                    .text(msg.getText() != null ? msg.getText() : "")
                    .level(msg.getLevel())
                    .timestamp(msg.getTimestamp() != null
                            ? msg.getTimestamp() : LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Log non persisté pour run {} : {}", run.getId(), e.getMessage());
        }
    }

    /** Ancienne signature conservée pour compatibilité interne */
    private void send(String topic, LogMessage msg) {
        messaging.convertAndSend(topic, msg);
    }

    private List<String> buildCommand(TestRun run, Path venvPath,
                                      Path projectPath, Path reportDir,
                                      int processes) {
        List<String> cmd = new ArrayList<>();
        boolean usePabot = run.isExecutedWithPabot();

        if (usePabot) {
            cmd.add(storageConfig.getPabotExecutable(venvPath)
                    .toAbsolutePath().normalize().toString());
            cmd.add("--processes"); cmd.add(String.valueOf(processes));
        } else {
            cmd.add(storageConfig.getRobotExecutable(venvPath)
                    .toAbsolutePath().normalize().toString());
        }

        cmd.add("--outputdir"); cmd.add(reportDir.toAbsolutePath().normalize().toString());
        cmd.add("--output");    cmd.add("output.xml");
        cmd.add("--log");       cmd.add("log.html");
        cmd.add("--report");    cmd.add("report.html");

        if (run.getMode() == ExecutionMode.SINGLE_TEST) {
            String[] parts = run.getTarget().split("::");
            if (parts.length == 2) {
                cmd.add("--test"); cmd.add(parts[1].trim());
                cmd.add(projectPath.resolve(parts[0].trim())
                        .toAbsolutePath().normalize().toString());
            } else {
                cmd.add(projectPath.resolve(run.getTarget())
                        .toAbsolutePath().normalize().toString());
            }
        } else {
            cmd.add(projectPath.resolve(run.getTarget())
                    .toAbsolutePath().normalize().toString());
        }

        return cmd;
    }

    /**
     * Configure les variables d'environnement pour que robot/pabot
     * utilise le venv du projet et non le Python système.
     * Cross-platform : Windows + Linux/Docker.
     */
    private void setupEnvironment(ProcessBuilder pb, Path venvPath,
                                  Map<String, String> dotEnvVars) {
        Map<String, String> env = pb.environment();

        // 1. Variables du .env
        env.putAll(dotEnvVars);

        // 2. Priorité au venv dans PATH
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String binDir = isWindows ? "Scripts" : "bin";
        Path venvBin  = venvPath.resolve(binDir).toAbsolutePath().normalize();

        String currentPath = env.getOrDefault("PATH", env.getOrDefault("Path", ""));
        env.put("PATH", venvBin + File.pathSeparator + currentPath);
        if (isWindows) {
            env.put("Path", venvBin + File.pathSeparator + currentPath);
        }

        // 3. PYTHONPATH → forcer les modules du venv
        Path sitePackages = resolveSitePackages(venvPath, isWindows);
        String currentPythonPath = env.getOrDefault("PYTHONPATH", "");
        env.put("PYTHONPATH", sitePackages + File.pathSeparator + currentPythonPath);

        // 4. VIRTUAL_ENV → indique à Python qu'un venv est actif
        env.put("VIRTUAL_ENV", venvPath.toAbsolutePath().normalize().toString());

        // 5. Supprimer PYTHONHOME pour éviter les conflits avec le Python système
        env.remove("PYTHONHOME");
    }

    /**
     * Résout le dossier site-packages du venv.
     * Windows : venv/Lib/site-packages
     * Linux/Docker : venv/lib/python3.X/site-packages (version dynamique)
     */
    private Path resolveSitePackages(Path venvPath, boolean isWindows) {
        if (isWindows) {
            return venvPath.resolve("Lib").resolve("site-packages")
                    .toAbsolutePath().normalize();
        }
        Path libDir = venvPath.resolve("lib");
        if (Files.exists(libDir)) {
            try (var stream = Files.list(libDir)) {
                return stream
                        .filter(p -> p.getFileName().toString().startsWith("python"))
                        .findFirst()
                        .map(p -> p.resolve("site-packages").toAbsolutePath().normalize())
                        .orElse(libDir.resolve("site-packages"));
            } catch (IOException e) {
                log.warn("Impossible de résoudre site-packages : {}", e.getMessage());
            }
        }
        return libDir.resolve("site-packages");
    }

    private LogMessage categorize(Long runId, String line) {
        String lower = line.toLowerCase();
        if (lower.contains("| fail |") || lower.contains("error") || lower.contains("failed")) {
            return LogMessage.error(runId, line);
        }
        if (lower.contains("| pass |") || lower.contains("passed")) {
            return LogMessage.success(runId, line);
        }
        if (lower.contains("warn")) {
            return LogMessage.warn(runId, line);
        }
        return LogMessage.info(runId, line);
    }

    private void finalize(TestRun run, RunStatus status, LocalDateTime start,
                          Path reportDir, int passed, int failed,
                          int skipped, String errorMsg) {
        run.setStatus(status);
        run.setPassed(passed);
        run.setFailed(failed);
        run.setSkipped(skipped);
        run.setFinishedAt(LocalDateTime.now());
        run.setDurationSeconds(ChronoUnit.SECONDS.between(start, run.getFinishedAt()));
        run.setReportPath("run-" + run.getId());
        run.setErrorMessage(errorMsg);
        runRepository.save(run);
    }

    private void errorFinalize(TestRun run, String errorMsg) {
        run.setStatus(RunStatus.ERROR);
        run.setErrorMessage(errorMsg);
        run.setFinishedAt(LocalDateTime.now());
        runRepository.save(run);
    }

    private String buildLabel(TestRunDto.LaunchRequest req) {
        if (req.getLabel() != null && !req.getLabel().isBlank()) return req.getLabel();
        if (req.getMode() == ExecutionMode.SINGLE_TEST) {
            String[] parts = req.getTarget().split("::");
            return parts.length == 2 ? parts[1].trim() : req.getTarget();
        }
        return req.getTarget();
    }

    public TestRunDto.Response toResponse(TestRun r) {
        TestRunDto.Response dto = new TestRunDto.Response();
        dto.setId(r.getId());
        dto.setProjectId(r.getProject().getId());
        dto.setProjectName(r.getProject().getName());
        dto.setMode(r.getMode());
        dto.setTarget(r.getTarget());
        dto.setLabel(r.getLabel());
        dto.setStatus(r.getStatus());
        dto.setPassed(r.getPassed());
        dto.setFailed(r.getFailed());
        dto.setSkipped(r.getSkipped());
        dto.setDurationSeconds(r.getDurationSeconds());
        dto.setReportPath(r.getReportPath());
        dto.setErrorMessage(r.getErrorMessage());
        dto.setExecutedWithPabot(r.isExecutedWithPabot());
        dto.setStartedAt(r.getStartedAt());
        dto.setFinishedAt(r.getFinishedAt());
        return dto;
    }
}