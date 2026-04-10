package com.testhub.service;

import com.testhub.config.StorageConfig;
import com.testhub.dto.LogMessage;
import com.testhub.entity.SetupLog;
import com.testhub.entity.TestProject;
import com.testhub.enums.VenvStatus;
import com.testhub.repository.SetupLogRepository;
import com.testhub.repository.TestProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Gère la création du venv Python et l'installation des dépendances.
 *
 * Corrections :
 *  1. venvStatus = ERROR si pip install échoue (plus de faux READY)
 *  2. Logs persistés en base → consultables après rechargement de page
 *
 * Canal WebSocket : /topic/projects/{projectId}/setup
 * Endpoint logs   : GET /api/projects/{id}/setup-logs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VenvSetupService {

    private final StorageConfig         storageConfig;
    private final TestProjectRepository projectRepository;
    private final SetupLogRepository    setupLogRepository;
    private final SimpMessagingTemplate messaging;

    @Async
    public void setupAsync(Long projectId) {
        TestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Projet introuvable : " + projectId));

        // Supprimer les anciens logs avant de recommencer
        setupLogRepository.deleteByProjectId(projectId);

        project.setVenvStatus(VenvStatus.INSTALLING);
        projectRepository.save(project);

        Path projectPath = Path.of(project.getStoragePath()).toAbsolutePath().normalize();
        Path venvPath    = projectPath.resolve("venv").toAbsolutePath().normalize();
        String topic     = "/topic/projects/" + projectId + "/setup";

        send(project, topic, LogMessage.system(projectId, "══════════════════════════════════════"));
        send(project, topic, LogMessage.system(projectId, "  Setup venv — " + project.getName()));
        send(project, topic, LogMessage.system(projectId, "══════════════════════════════════════"));

        try {
            // ── Vérification Python disponible ───────────────────────────────
            send(project, topic, LogMessage.info(projectId,
                    "▶ Python : " + storageConfig.getPythonExecutable()));

            // ── Étape 1 : Créer le venv ──────────────────────────────────────
            send(project, topic, LogMessage.info(projectId, "▶ Création du venv…"));

            int rc = runCommand(
                    List.of(storageConfig.getPythonExecutable(),
                            "-m", "venv", venvPath.toString()),
                    projectPath, topic, project);

            if (rc != 0) {
                fail(project, venvPath, topic,
                        "Échec création du venv (exit " + rc + ") — " +
                                "Python est-il installé ? " +
                                "Commande : " + storageConfig.getPythonExecutable());
                return;
            }
            send(project, topic, LogMessage.success(projectId, "✔ Venv créé : " + venvPath));

            // ── Vérification que le venv existe bien ─────────────────────────
            Path pythonInVenv = storageConfig.getPythonExecutableInVenv(venvPath)
                    .toAbsolutePath().normalize();
            if (!Files.exists(pythonInVenv)) {
                fail(project, venvPath, topic,
                        "Python introuvable dans le venv : " + pythonInVenv +
                                " — vérifiez la version Python installée sur le système");
                return;
            }

            // ── Étape 2 : Mettre à jour pip ──────────────────────────────────
            send(project, topic, LogMessage.info(projectId, "▶ Mise à jour de pip…"));
            runCommand(
                    List.of(pythonInVenv.toString(),
                            "-m", "pip", "install", "--upgrade", "pip", "--quiet"),
                    projectPath, topic, project);

            // ── Étape 3 : Installer requirements.txt ─────────────────────────
            Path requirements = projectPath.resolve("requirements.txt");
            if (Files.exists(requirements)) {
                send(project, topic, LogMessage.info(projectId,
                        "▶ Installation de requirements.txt…"));
                send(project, topic, LogMessage.info(projectId,
                        "  Fichier : " + requirements));

                rc = runCommand(
                        List.of(pythonInVenv.toString(),
                                "-m", "pip", "install", "-r", requirements.toString()),
                        projectPath, topic, project);

                if (rc != 0) {
                    fail(project, venvPath, topic,
                            "Échec pip install -r requirements.txt (exit " + rc + ") — " +
                                    "Vérifiez le contenu du fichier requirements.txt et " +
                                    "la connexion internet du serveur");
                    return;
                }
                send(project, topic, LogMessage.success(projectId,
                        "✔ Dépendances installées"));
            } else {
                send(project, topic, LogMessage.warn(projectId,
                        "⚠ Aucun requirements.txt trouvé dans : " + projectPath +
                                " — le venv sera vide, les tests risquent d'échouer"));
            }

            // ── Étape 4 : Détecter pabot ─────────────────────────────────────
            boolean hasPabot = Files.exists(
                    storageConfig.getPabotExecutable(venvPath).toAbsolutePath().normalize());
            if (hasPabot) {
                send(project, topic, LogMessage.info(projectId,
                        "✔ pabot détecté — exécution parallèle disponible"));
            } else {
                send(project, topic, LogMessage.info(projectId,
                        "ℹ pabot non détecté — exécution séquentielle (robot)"));
            }

            // ── Finalisation ─────────────────────────────────────────────────
            project.setVenvStatus(VenvStatus.READY);
            project.setVenvPath(venvPath.toString());
            project.setVenvCreatedAt(LocalDateTime.now());
            project.setUsesPabot(hasPabot);
            project.setVenvError(null);
            projectRepository.save(project);

            send(project, topic, LogMessage.success(projectId,
                    "══ Venv prêt — le projet peut être exécuté ══"));

        } catch (Exception e) {
            log.error("Erreur setup venv projet {}", projectId, e);
            fail(project, venvPath, topic, "Erreur inattendue : " + e.getMessage());
        }
    }

    @Async
    public void reinstallAsync(Long projectId) {
        TestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Projet introuvable : " + projectId));

        if (project.getVenvPath() != null) {
            try { deleteDir(Path.of(project.getVenvPath())); }
            catch (IOException e) { log.warn("Impossible de supprimer le venv : {}", e.getMessage()); }
        }
        project.setVenvStatus(VenvStatus.NONE);
        project.setVenvPath(null);
        project.setVenvError(null);
        projectRepository.save(project);

        setupAsync(projectId);
    }

    /**
     * Retourne les logs persistés du dernier setup.
     * Appelé par le frontend au montage de l'onglet "Setup venv".
     */
    public List<LogMessage> getSetupLogs(Long projectId) {
        return setupLogRepository.findByProjectIdOrderByIdAsc(projectId)
                .stream()
                .map(l -> LogMessage.builder()
                        .sourceId(projectId)
                        .text(l.getText())
                        .level(l.getLevel())
                        .timestamp(l.getTimestamp())
                        .build())
                .toList();
    }

    // ── Privé ────────────────────────────────────────────────────────────────

    /**
     * Envoie un log via WebSocket ET le persiste en base.
     */
    private void send(TestProject project, String topic, LogMessage msg) {
        messaging.convertAndSend(topic, msg);
        try {
            setupLogRepository.save(SetupLog.builder()
                    .project(project)
                    .text(msg.getText() != null ? msg.getText() : "")
                    .level(msg.getLevel())
                    .timestamp(msg.getTimestamp() != null
                            ? msg.getTimestamp() : LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Log setup non persisté : {}", e.getMessage());
        }
    }

    private int runCommand(List<String> command, Path workDir,
                           String topic, TestProject project)
            throws IOException, InterruptedException {

        Path absoluteWorkDir = workDir.toAbsolutePath().normalize();

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(absoluteWorkDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                // Catégoriser selon le contenu
                LogMessage msg;
                String lower = trimmed.toLowerCase();
                if (lower.contains("error") || lower.contains("failed") ||
                        lower.contains("could not")) {
                    msg = LogMessage.error(project.getId(), trimmed);
                } else if (lower.contains("warning") || lower.contains("warn")) {
                    msg = LogMessage.warn(project.getId(), trimmed);
                } else if (lower.contains("successfully") || lower.contains("installed")) {
                    msg = LogMessage.success(project.getId(), trimmed);
                } else {
                    msg = LogMessage.info(project.getId(), trimmed);
                }
                send(project, topic, msg);
            }
        }

        boolean done = process.waitFor(10, TimeUnit.MINUTES);
        if (!done) {
            process.destroyForcibly();
            return -1;
        }
        return process.exitValue();
    }

    private void fail(TestProject project, Path venvPath, String topic, String reason) {
        send(project, topic, LogMessage.error(project.getId(), "✘ " + reason));
        send(project, topic, LogMessage.system(project.getId(),
                "══ Installation échouée — consultez les logs ci-dessus ══"));

        project.setVenvStatus(VenvStatus.ERROR);
        project.setVenvError(reason);
        projectRepository.save(project);

        // Nettoyer le venv partiel
        try { deleteDir(venvPath); } catch (IOException ignored) {}
    }

    private void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
    }
}