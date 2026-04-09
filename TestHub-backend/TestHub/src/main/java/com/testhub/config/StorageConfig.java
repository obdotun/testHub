package com.testhub.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Configuration
@Getter
public class StorageConfig {

    @Value("${robot.storage.projects-dir}")
    private String projectsDir;

    @Value("${robot.storage.reports-dir}")
    private String reportsDir;

    @Value("${robot.python.executable:python3}")
    private String pythonExecutable;

    @Value("${robot.execution.timeout-seconds:600}")
    private int executionTimeoutSeconds;

    @Value("${robot.execution.excluded-dirs:venv,__pycache__,Results,pabot_results,.git}")
    private String excludedDirsRaw;

    public Path getProjectsPath() {
        return Paths.get(projectsDir).toAbsolutePath().normalize();
    }

    public Path getReportsPath() {
        return Paths.get(reportsDir).toAbsolutePath().normalize();
    }

    /** Liste des dossiers à exclure lors de l'extraction d'un ZIP */
    public List<String> getExcludedDirs() {
        return Arrays.asList(excludedDirsRaw.split(","));
    }

    /**
     * Chemin vers l'exécutable robot dans un venv donné.
     * Windows : venv/Scripts/robot.exe
     * Linux/Mac : venv/bin/robot
     */
    public Path getRobotExecutable(Path venvPath) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String subdir = isWindows ? "Scripts" : "bin";
        String exe    = isWindows ? "robot.exe" : "robot";
        return venvPath.resolve(subdir).resolve(exe);
    }

    /** Chemin vers pabot dans un venv donné */
    public Path getPabotExecutable(Path venvPath) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String subdir = isWindows ? "Scripts" : "bin";
        String exe    = isWindows ? "pabot.exe" : "pabot";
        return venvPath.resolve(subdir).resolve(exe);
    }

    /** Chemin vers pip dans un venv donné */
    public Path getPipExecutable(Path venvPath) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String subdir = isWindows ? "Scripts" : "bin";
        String exe    = isWindows ? "pip.exe" : "pip";
        Path pipPath  = venvPath.resolve(subdir).resolve(exe);

        // Fallback : utiliser python -m pip si pip.exe introuvable
        if (!pipPath.toFile().exists()) {
            return getPythonExecutableInVenv(venvPath);
        }
        return pipPath;
    }

    /** Python dans le venv */
    public Path getPythonExecutableInVenv(Path venvPath) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String subdir = isWindows ? "Scripts" : "bin";
        String exe    = isWindows ? "python.exe" : "python";
        return venvPath.resolve(subdir).resolve(exe);
    }

    @PostConstruct
    public void initDirs() throws IOException {
        Files.createDirectories(getProjectsPath());
        Files.createDirectories(getReportsPath());
        System.out.println("[StorageConfig] Projets  : " + getProjectsPath());
        System.out.println("[StorageConfig] Rapports : " + getReportsPath());
        System.out.println("[StorageConfig] Python   : " + pythonExecutable);
    }
}