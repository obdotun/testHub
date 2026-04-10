package com.testhub.service;

import com.testhub.dto.LogMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Gère les opérations Git (clone, pull, listBranches) depuis Bitbucket en HTTPS.
 * Compatible Windows et Linux/Docker.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitService {

    private final SimpMessagingTemplate messaging;

    /**
     * Retourne la liste des branches disponibles dans un repo distant.
     * Utilise git ls-remote --heads — ne clone pas le repo, très rapide.
     *
     * @param repositoryUrl URL publique du repo
     * @param username      Bitbucket username
     * @param appPassword   Bitbucket App Password
     * @return liste des noms de branches (ex: ["main", "develop", "TRN", "UAT"])
     */
    public List<String> listBranches(String repositoryUrl,
                                     String username,
                                     String appPassword)
            throws IOException, InterruptedException {

        String authenticatedUrl = injectCredentials(repositoryUrl, username, appPassword);

        // -c http.sslVerify=false → nécessaire pour les instances Bitbucket Server
        // avec certificats SSL auto-signés (ex: bitbucket.guce.gouv.ci)
        List<String> cmd = List.of(
                "git", "-c", "http.sslVerify=false",
                "ls-remote", "--heads", authenticatedUrl);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.redirectErrorStream(false);

        Process process = pb.start();

        List<String> branches = new ArrayList<>();

        // Lire stdout — format : "{hash}\trefs/heads/{branchName}"
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Extraire le nom de branche depuis "refs/heads/main"
                if (line.contains("refs/heads/")) {
                    String branch = line.substring(
                            line.indexOf("refs/heads/") + "refs/heads/".length()).trim();
                    if (!branch.isEmpty()) {
                        branches.add(branch);
                    }
                }
            }
        }

        // Lire stderr pour détecter les erreurs d'authentification
        String stderr = new String(process.getErrorStream().readAllBytes()).trim();

        boolean done = process.waitFor(30, TimeUnit.SECONDS);
        if (!done) {
            process.destroyForcibly();
            throw new RuntimeException("Timeout lors de la récupération des branches");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            // Masquer les credentials dans le message d'erreur
            String safeErr = stderr.replace(authenticatedUrl, repositoryUrl);
            if (safeErr.contains("Authentication failed") ||
                    safeErr.contains("could not read Username")) {
                throw new RuntimeException(
                        "Authentification échouée — vérifiez votre username et App Password");
            }
            if (safeErr.contains("not found") || safeErr.contains("Repository not found")) {
                throw new RuntimeException(
                        "Repository introuvable : " + repositoryUrl);
            }
            throw new RuntimeException("Erreur git ls-remote (exit " + exitCode + ") : " + safeErr);
        }

        if (branches.isEmpty()) {
            throw new RuntimeException(
                    "Aucune branche trouvée — vérifiez l'URL du repo");
        }

        log.info("Branches récupérées depuis {} : {}", repositoryUrl, branches);
        return branches;
    }

    /**
     * Clone un repo Bitbucket privé dans destDir.
     */
    public void clone(String repositoryUrl, String username, String appPassword,
                      String branch, Path destDir, String topic, Long projectId)
            throws IOException, InterruptedException {

        String authenticatedUrl = injectCredentials(repositoryUrl, username, appPassword);

        send(topic, LogMessage.info(projectId,
                "▶ Clonage depuis : " + repositoryUrl));
        send(topic, LogMessage.info(projectId, "  Branche : " + branch));

        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-c"); cmd.add("http.sslVerify=false"); // certificat SSL auto-signé
        cmd.add("clone");
        cmd.add("--branch"); cmd.add(branch);
        cmd.add("--depth");  cmd.add("1");
        cmd.add("--single-branch");
        cmd.add(authenticatedUrl);
        cmd.add(destDir.toAbsolutePath().normalize().toString());

        int rc = runGitCommand(cmd, destDir.getParent(), topic, projectId,
                authenticatedUrl, repositoryUrl);
        if (rc != 0) {
            throw new RuntimeException("Échec du clone git (exit " + rc + ")");
        }
        send(topic, LogMessage.success(projectId, "✔ Clone terminé"));
    }

    /**
     * Met à jour un repo déjà cloné (git pull).
     */
    public void pull(Path projectPath, String username, String appPassword,
                     String branch, String repositoryUrl,
                     String topic, Long projectId)
            throws IOException, InterruptedException {

        send(topic, LogMessage.info(projectId,
                "▶ Mise à jour depuis : " + repositoryUrl));

        String authenticatedUrl = injectCredentials(repositoryUrl, username, appPassword);

        List<String> setUrlCmd = List.of(
                "git", "-c", "http.sslVerify=false",
                "remote", "set-url", "origin", authenticatedUrl);
        runGitCommand(setUrlCmd, projectPath, topic, projectId,
                authenticatedUrl, repositoryUrl);

        List<String> pullCmd = List.of(
                "git", "-c", "http.sslVerify=false",
                "pull", "origin", branch, "--rebase");
        int rc = runGitCommand(pullCmd, projectPath, topic, projectId,
                authenticatedUrl, repositoryUrl);
        if (rc != 0) {
            throw new RuntimeException("Échec du git pull (exit " + rc + ")");
        }
        send(topic, LogMessage.success(projectId, "✔ Mise à jour terminée"));
    }

    /**
     * Récupère le hash du dernier commit.
     */
    public String getLastCommit(Path projectPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "rev-parse", "--short", "HEAD");
            pb.directory(projectPath.toAbsolutePath().normalize().toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String hash = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(10, TimeUnit.SECONDS);
            return hash;
        } catch (Exception e) {
            log.warn("Impossible de lire le commit : {}", e.getMessage());
            return null;
        }
    }

    // ── Privé ────────────────────────────────────────────────────────────────

    private String injectCredentials(String url, String username, String appPassword) {
        if (username == null || username.isBlank() ||
                appPassword == null || appPassword.isBlank()) {
            return url;
        }
        try {
            URI uri = new URI(url);

            // Encoder les caractères spéciaux du username
            // Cas fréquent : adresses email (ex: user@company.com → user%40company.com)
            String encodedUsername = username
                    .replace("@",  "%40")
                    .replace("\\", "%5C")
                    .replace(" ",  "%20");

            // Encoder les caractères spéciaux du mot de passe / jeton
            String encodedPassword = appPassword
                    .replace("@", "%40")
                    .replace("#", "%23")
                    .replace(":", "%3A")
                    .replace(" ", "%20");

            return uri.getScheme() + "://"
                    + encodedUsername + ":" + encodedPassword + "@"
                    + uri.getHost()
                    + (uri.getPort() != -1 ? ":" + uri.getPort() : "")
                    + uri.getPath();
        } catch (Exception e) {
            log.error("URL invalide : {}", url);
            return url;
        }
    }

    private int runGitCommand(List<String> command, Path workDir,
                              String topic, Long projectId,
                              String authenticatedUrl, String publicUrl)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toAbsolutePath().normalize().toFile());
        pb.redirectErrorStream(true);
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String safeLine = authenticatedUrl != null
                        ? line.replace(authenticatedUrl, publicUrl)
                        : line;
                if (!safeLine.trim().isEmpty()) {
                    send(topic, LogMessage.info(projectId, safeLine));
                }
            }
        }

        boolean done = process.waitFor(5, TimeUnit.MINUTES);
        if (!done) { process.destroyForcibly(); return -1; }
        return process.exitValue();
    }

    private void send(String topic, LogMessage msg) {
        messaging.convertAndSend(topic, msg);
    }
}