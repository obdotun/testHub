package com.testhub.service;

import com.testhub.config.StorageConfig;
import com.testhub.dto.LogMessage;
import com.testhub.entity.TestProject;
import com.testhub.enums.VenvStatus;
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
 * Gère la création du venv Python et l'installation des dépendances
 * pour un projet, avec streaming des logs via WebSocket.
 *
 * Canal WebSocket : /topic/projects/{projectId}/setup
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VenvSetupService {

    private final StorageConfig             storageConfig;
    private final TestProjectRepository     projectRepository;
    private final SimpMessagingTemplate     messaging;

    /**
     * Lance le setup du venv en arrière-plan.
     * Appelé après l'extraction du ZIP.
     */
    @Async
    public void setupAsync(Long projectId) {
        TestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Projet introuvable : " + projectId));

        project.setVenvStatus(VenvStatus.INSTALLING);
        projectRepository.save(project);

        // ── Chemins absolus normalisés (cross-platform Windows + Linux/Docker) ──
        Path projectPath = Path.of(project.getStoragePath()).toAbsolutePath().normalize();
        Path venvPath    = projectPath.resolve("venv").toAbsolutePath().normalize();
        String topic     = "/topic/projects/" + projectId + "/setup";

        send(topic, LogMessage.system(projectId, "══════════════════════════════════════"));
        send(topic, LogMessage.system(projectId, "  Setup venv — " + project.getName()));
        send(topic, LogMessage.system(projectId, "══════════════════════════════════════"));

        try {
            // ── Étape 1 : Créer le venv ─────────────────────────────────────
            // Commande : python /abs/path/venv  → absolu, pas de cmd /c nécessaire
            send(topic, LogMessage.info(projectId,
                    "▶ Création du venv avec " + storageConfig.getPythonExecutable()));

            int rc = runCommand(
                    List.of(storageConfig.getPythonExecutable(), "-m", "venv",
                            venvPath.toString()),
                    projectPath, topic, projectId);

            if (rc != 0) {
                fail(project, venvPath, topic, "Échec de la création du venv (exit " + rc + ")");
                return;
            }
            send(topic, LogMessage.success(projectId, "✔ Venv créé : " + venvPath));

            // ── Étape 2 : Mettre à jour pip ──────────────────────────────────
            // Utilise python -m pip (plus fiable que pip.exe direct sur Windows)
            // et fonctionne identiquement sur Linux/Docker
            Path pythonInVenv = storageConfig.getPythonExecutableInVenv(venvPath)
                    .toAbsolutePath().normalize();

            send(topic, LogMessage.info(projectId, "▶ Mise à jour de pip…"));
            runCommand(
                    List.of(pythonInVenv.toString(),
                            "-m", "pip", "install", "--upgrade", "pip", "--quiet"),
                    projectPath, topic, projectId);

            // ── Étape 3 : Installer requirements.txt ─────────────────────────
            Path requirements = projectPath.resolve("requirements.txt");
            if (Files.exists(requirements)) {
                send(topic, LogMessage.info(projectId,
                        "▶ Installation de requirements.txt…"));

                rc = runCommand(
                        List.of(pythonInVenv.toString(),
                                "-m", "pip", "install", "-r", requirements.toString()),
                        projectPath, topic, projectId);

                if (rc != 0) {
                    fail(project, venvPath, topic,
                            "Échec pip install -r requirements.txt (exit " + rc + ")");
                    return;
                }
                send(topic, LogMessage.success(projectId, "✔ Dépendances installées"));
            } else {
                send(topic, LogMessage.warn(projectId,
                        "⚠ Aucun requirements.txt trouvé — venv vide"));
            }

            // ── Étape 4 : Détecter pabot ─────────────────────────────────────
            boolean hasPabot = Files.exists(
                    storageConfig.getPabotExecutable(venvPath).toAbsolutePath().normalize());
            if (hasPabot) {
                send(topic, LogMessage.info(projectId,
                        "ℹ pabot détecté — exécution parallèle disponible"));
            }

            // ── Finalisation ─────────────────────────────────────────────────
            project.setVenvStatus(VenvStatus.READY);
            project.setVenvPath(venvPath.toString());
            project.setVenvCreatedAt(LocalDateTime.now());
            project.setUsesPabot(hasPabot);
            project.setVenvError(null);
            projectRepository.save(project);

            send(topic, LogMessage.success(projectId,
                    "══ Venv prêt — le projet peut être exécuté ══"));

        } catch (Exception e) {
            log.error("Erreur setup venv projet {}", projectId, e);
            fail(project, venvPath, topic, "Erreur inattendue : " + e.getMessage());
        }
    }

    /**
     * Relance le setup (utile si requirements.txt a changé).
     */
    @Async
    public void reinstallAsync(Long projectId) {
        TestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Projet introuvable : " + projectId));

        // Supprimer le venv existant
        if (project.getVenvPath() != null) {
            try {
                deleteDir(Path.of(project.getVenvPath()));
            } catch (IOException e) {
                log.warn("Impossible de supprimer le venv : {}", e.getMessage());
            }
        }
        project.setVenvStatus(VenvStatus.NONE);
        project.setVenvPath(null);
        projectRepository.save(project);

        setupAsync(projectId);
    }

    // ── Privé ───────────────────────────────────────────────────────────────

    /**
     * Exécute une commande et streame sa sortie ligne par ligne.
     * Retourne le code de retour du processus.
     */
    private int runCommand(List<String> command, Path workDir,
                           String topic, Long projectId) throws IOException, InterruptedException {

        // Normaliser le workDir en chemin absolu (cross-platform Windows + Linux/Docker)
        // Pas de cmd /c — les commandes passées utilisent déjà des chemins absolus :
        //   python3 -m venv /abs/path/venv
        //   /abs/path/venv/Scripts/pip.exe install ...   (Windows)
        //   /abs/path/venv/bin/pip install ...           (Linux/Docker)
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
                LogMessage msg = trimmed.toLowerCase().contains("error")
                        ? LogMessage.error(projectId, trimmed)
                        : LogMessage.info(projectId, trimmed);
                send(topic, msg);
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
        send(topic, LogMessage.error(project.getId(), "✘ " + reason));
        project.setVenvStatus(VenvStatus.ERROR);
        project.setVenvError(reason);
        projectRepository.save(project);
        // Nettoyer le venv partiel
        try { deleteDir(venvPath); } catch (IOException ignored) {}
    }

    private void send(String topic, LogMessage msg) {
        messaging.convertAndSend(topic, msg);
    }

    private void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
    }
}