package com.testhub.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Parse les fichiers .robot sans dépendance externe.
 * Extrait les test cases, leur position et leurs tags.
 */
@Component
public class RobotFileParser {

    private static final Pattern SECTION =
            Pattern.compile("^\\*{3}\\s*Test Cases?\\s*\\*{3}.*", Pattern.CASE_INSENSITIVE);

    /**
     * Extrait les test cases d'un fichier .robot.
     * Retourne une map : nom → tags (peut être vide).
     */
    public List<ParsedTestCase> parse(Path robotFile) throws IOException {
        List<ParsedTestCase> result = new ArrayList<>();
        boolean inTestSection = false;
        String currentName = null;
        List<String> currentTags = new ArrayList<>();
        int position = 0;

        for (String line : Files.readAllLines(robotFile)) {
            // Détecter changement de section
            if (line.trim().startsWith("***")) {
                // Sauvegarder le test case courant avant de changer de section
                if (currentName != null) {
                    result.add(new ParsedTestCase(++position, currentName,
                            String.join(", ", currentTags)));
                    currentName = null;
                    currentTags = new ArrayList<>();
                }
                inTestSection = SECTION.matcher(line.trim()).matches();
                continue;
            }

            if (!inTestSection) continue;

            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            // Ligne non indentée = nouveau test case
            if (!line.startsWith(" ") && !line.startsWith("\t") && !trimmed.isEmpty()) {
                // Sauvegarder le précédent
                if (currentName != null) {
                    result.add(new ParsedTestCase(++position, currentName,
                            String.join(", ", currentTags)));
                }
                currentName = trimmed;
                currentTags = new ArrayList<>();
            }
            // Ligne indentée avec [Tags] = tags du test courant
            else if (trimmed.startsWith("[Tags]") && currentName != null) {
                String tagsPart = trimmed.substring("[Tags]".length()).trim();
                Arrays.stream(tagsPart.split("\\s{2,}|\\t"))
                        .map(String::trim)
                        .filter(t -> !t.isEmpty())
                        .forEach(currentTags::add);
            }
        }

        // Dernier test case
        if (currentName != null) {
            result.add(new ParsedTestCase(++position, currentName,
                    String.join(", ", currentTags)));
        }

        return result;
    }

    /** Nom de suite = nom du fichier sans extension */
    public String suiteName(Path robotFile) {
        String name = robotFile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public record ParsedTestCase(int position, String name, String tags) {}
}