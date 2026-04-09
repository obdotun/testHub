package com.testhub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Lit le fichier .env d'un projet et retourne les variables
 * sous forme de Map pour les injecter dans le ProcessBuilder.
 *
 * Format supporté :
 *   URL=https://monapp.ci
 *   BROWSER=chromium
 *   # commentaire ignoré
 *   HEADLESS=true
 */
@Slf4j
@Component
public class DotEnvLoader {

    /**
     * Charge le fichier .env depuis la racine du projet.
     * Retourne une Map vide si le fichier n'existe pas.
     */
    public Map<String, String> load(Path projectPath) {
        Path envFile = projectPath.resolve(".env");
        Map<String, String> vars = new HashMap<>();

        if (!Files.exists(envFile)) {
            log.debug(".env absent dans {}", projectPath);
            return vars;
        }

        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();

                // Ignorer commentaires et lignes vides
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;

                String key   = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();

                // Supprimer les guillemets éventuels : "valeur" → valeur
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }

                vars.put(key, value);
            }
            log.info(".env chargé : {} variables depuis {}", vars.size(), envFile);
        } catch (IOException e) {
            log.warn("Impossible de lire .env : {}", e.getMessage());
        }

        return vars;
    }
}