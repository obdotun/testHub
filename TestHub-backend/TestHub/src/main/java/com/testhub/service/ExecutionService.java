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


@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final TestRunRepository     runRepository;
    private final TestProjectRepository projectRepository;
    private final StorageConfig         storageConfig;
    private final SimpMessagingTemplate messaging;
    private final DotEnvLoader          dotEnvLoader;
    private final OutputXmlParser       outputXmlParser;
    private final RunLogRepository runLogRepository;

    /**
     * Crée le TestRun en base et lance l'exécution en arrière-plan.
     * Retourne immédiatement avec le run en statut PENDING.
     */
    public TestRunDto.Response launchRun(TestRunDto.LaunchRequest req) {
        TestProject project = projectRepository.findById(req.getProjectId())
                .orElseThrow(() -> new RuntimeException(
                        "Projet introuvable : id=" + req.getProjectId()));

        // Vérifier que le venv est prêt
        if (project.getVenvStatus() != VenvStatus.READY) {
            throw new IllegalStateException(
                    "Le venv du projet n'est pas prêt (statut : " +
                            project.getVenvStatus() + "). " +
                            "Attendez la fin de l'installation ou relancez via /reinstall-venv.");
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

        // Lancer en arrière-plan — non bloquant
        executeAsync(run.getId(), req.getProcesses());

        return toResponse(run);
    }

    /**
     * Exécution asynchrone — tourne dans un thread séparé (@Async).
     * Streame chaque ligne de sortie en WebSocket.
     * Canal : /topic/runs/{runId}/logs
     */
    @Async
    public void executeAsync(Long runId, int processes) {
        TestRun run = runRepository.findById(runId)
                .orElseThrow(() -> new RuntimeException("Run introuvable : " + runId));

        String topic = "/topic/runs/" + runId + "/logs";

        run.setStatus(RunStatus.RUNNING);
        runRepository.save(run);

        TestProject project = run.getProject();

        // ── Chemins absolus normalisés (cross-platform Windows + Linux/Docker) ──
        Path projectPath = Path.of(project.getStoragePath()).toAbsolutePath().normalize();
        Path venvPath    = Path.of(project.getVenvPath()).toAbsolutePath().normalize();
        Path reportDir   = storageConfig.getReportsPath().resolve("run-" + runId)
                .toAbsolutePath().normalize();

        send(topic, LogMessage.system(runId, "══════════════════════════════════════"));
        send(topic, LogMessage.system(runId, "  Exécution #" + runId + " — " + run.getLabel()));
        send(topic, LogMessage.system(runId, "══════════════════════════════════════"));

        // ── Vérification existence du dossier projet ─────────────────────────
        if (!Files.exists(projectPath) || !Files.isDirectory(projectPath)) {
            String msg = "Dossier projet introuvable : " + projectPath;
            send(topic, LogMessage.error(runId, msg));
            run.setStatus(RunStatus.ERROR);
            run.setErrorMessage(msg);
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
            return;
        }

        // ── Vérification existence du venv ───────────────────────────────────
        if (!Files.exists(venvPath) || !Files.isDirectory(venvPath)) {
            String msg = "Venv introuvable : " + venvPath + " — relancez l'installation du venv";
            send(topic, LogMessage.error(runId, msg));
            run.setStatus(RunStatus.ERROR);
            run.setErrorMessage(msg);
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
            return;
        }

        try {
            Files.createDirectories(reportDir);

            // ── Construire la commande ───────────────────────────────────────
            // buildCommand utilise storageConfig.getRobotExecutable(venvPath)
            // qui retourne venv/Scripts/robot.exe (Windows) ou venv/bin/robot (Linux)
            // → aucun cmd /c nécessaire, l'exécutable est absolu
            List<String> command = buildCommand(run, venvPath, projectPath, reportDir, processes);

            send(topic, LogMessage.system(runId,
                    "Commande : " + String.join(" ", command)));

            // ── Charger le .env ─────────────────────────────────────────────
            Map<String, String> envVars = dotEnvLoader.load(projectPath);
            if (!envVars.isEmpty()) {
                send(topic, LogMessage.info(runId,
                        "Variables .env chargées : " + envVars.size()));
            }

            // ── Démarrer le processus ────────────────────────────────────────
            // Pas de cmd /c — l'exécutable robot/pabot est en chemin absolu
            // Fonctionne identiquement sur Windows, Linux et Docker
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectPath.toFile()); // chemin absolu normalisé ✅
            pb.redirectErrorStream(true);        // merge stdout + stderr
            setupEnvironment(pb, venvPath, envVars);    // variables du .env

            LocalDateTime start   = LocalDateTime.now();
            Process       process = pb.start();

            // ── Streamer les logs ligne par ligne ────────────────────────────
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    send(topic, categorize(runId, line));
                }
            }

            // ── Attendre la fin (avec timeout) ───────────────────────────────
            boolean finished = process.waitFor(
                    storageConfig.getExecutionTimeoutSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                finalize(run, RunStatus.ERROR, start, reportDir,
                        0, 0, 0, "Timeout dépassé (" +
                                storageConfig.getExecutionTimeoutSeconds() + "s)");
                send(topic, LogMessage.error(runId, "✘ Timeout dépassé"));
                return;
            }

            // ── Lire les résultats depuis output.xml ─────────────────────────
            OutputXmlParser.Result result = outputXmlParser.parse(reportDir);
            RunStatus status = result.allPassed() ? RunStatus.PASSED : RunStatus.FAILED;

            finalize(run, status, start, reportDir,
                    result.passed(), result.failed(), result.skipped(), null);

            String summary = String.format(
                    "Résultat : %d passés | %d échoués | %d ignorés",
                    result.passed(), result.failed(), result.skipped());
            send(topic, status == RunStatus.PASSED
                    ? LogMessage.success(runId, "✔ " + summary)
                    : LogMessage.error(runId,   "✘ " + summary));

        } catch (Exception e) {
            log.error("Erreur lors du run {}", runId, e);
            send(topic, LogMessage.error(runId, "Erreur système : " + e.getMessage()));
            run.setStatus(RunStatus.ERROR);
            run.setErrorMessage(e.getMessage());
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
        }

        send(topic, LogMessage.system(runId,
                "══ Fin exécution : " + run.getStatus() + " ══"));
    }

    // ── Construction de la commande ─────────────────────────────────────────

    /**
     * Construit la commande d'exécution robot/pabot.
     * Utilise des chemins absolus — cross-platform Windows + Linux/Docker.
     * Suppression de --rerunfailed (cause échec si output.xml absent).
     */
    private List<String> buildCommand(TestRun run, Path venvPath,
                                      Path projectPath, Path reportDir,
                                      int processes) {
        List<String> cmd = new ArrayList<>();
        boolean usePabot = run.isExecutedWithPabot();

        if (usePabot) {
            cmd.add(storageConfig.getPabotExecutable(venvPath)
                    .toAbsolutePath().normalize().toString());
            cmd.add("--processes");
            cmd.add(String.valueOf(processes));
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
     * Configure les variables d'environnement du ProcessBuilder
     * pour que robot/pabot utilise le venv du projet et non le Python système.
     *
     * Cross-platform :
     *   Windows : venv/Scripts/ + venv/Lib/site-packages/
     *   Linux/Docker : venv/bin/ + venv/lib/pythonX.Y/site-packages/
     */
    private void setupEnvironment(ProcessBuilder pb, Path venvPath,
                                  Map<String, String> dotEnvVars) {

        Map<String, String> env = pb.environment();

        // ── 1. Variables du .env du projet ───────────────────────────────────
        env.putAll(dotEnvVars);

        // ── 2. Priorité au venv dans PATH ────────────────────────────────────
        // Windows : venv/Scripts   | Linux/Docker : venv/bin
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().contains("win");
        String binDir = isWindows ? "Scripts" : "bin";
        Path venvBin  = venvPath.resolve(binDir).toAbsolutePath().normalize();

        String currentPath = env.getOrDefault("PATH",
                env.getOrDefault("Path", ""));
        env.put("PATH", venvBin + File.pathSeparator + currentPath);
        if (isWindows) {
            // Windows a parfois "Path" au lieu de "PATH"
            env.put("Path", venvBin + File.pathSeparator + currentPath);
        }

        // ── 3. PYTHONPATH → forcer l'utilisation du venv ─────────────────────
        // Windows : venv/Lib/site-packages
        // Linux   : venv/lib/pythonX.Y/site-packages (détection dynamique)
        Path sitePackages = resolveSitePackages(venvPath, isWindows);
        String currentPythonPath = env.getOrDefault("PYTHONPATH", "");
        env.put("PYTHONPATH", sitePackages + File.pathSeparator + currentPythonPath);

        // ── 4. VIRTUAL_ENV → indique à Python qu'un venv est actif ───────────
        env.put("VIRTUAL_ENV", venvPath.toAbsolutePath().normalize().toString());

        // ── 5. Désactiver l'héritage du Python système ───────────────────────
        // Empêche pabot/robot de résoudre des modules hors du venv
        env.remove("PYTHONHOME");
    }

    /**
     * Résout le dossier site-packages du venv.
     *
     * Windows : venv/Lib/site-packages  (fixe)
     * Linux   : venv/lib/python3.X/site-packages  (version dynamique)
     */
    private Path resolveSitePackages(Path venvPath, boolean isWindows) {
        if (isWindows) {
            return venvPath.resolve("Lib").resolve("site-packages")
                    .toAbsolutePath().normalize();
        }
        // Linux/Docker : chercher le dossier python3.X dynamiquement
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

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Catégorise une ligne de log selon son contenu */
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

    private void send(String topic, LogMessage msg) {
        // 1. Streamer via WebSocket (temps réel)
        messaging.convertAndSend(topic, msg);

        // 2. Persister en base (pour chargement ultérieur)
        if (msg.getSourceId() != null) {
            runRepository.findById(msg.getSourceId()).ifPresent(run -> {
                RunLog log = RunLog.builder()
                        .run(run)
                        .text(msg.getText())
                        .level(msg.getLevel())
                        .build();
                runLogRepository.save(log);
            });
        }
    }

    private String buildLabel(TestRunDto.LaunchRequest req) {
        if (req.getLabel() != null && !req.getLabel().isBlank()) {
            return req.getLabel();
        }
        if (req.getMode() == ExecutionMode.SINGLE_TEST) {
            String[] parts = req.getTarget().split("::");
            return parts.length == 2 ? parts[1].trim() : req.getTarget();
        }
        return req.getTarget();
    }

    // ── Mapper ───────────────────────────────────────────────────────────────

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