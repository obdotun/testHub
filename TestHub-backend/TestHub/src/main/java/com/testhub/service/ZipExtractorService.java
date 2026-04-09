package com.testhub.service;

import com.testhub.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extrait un fichier ZIP dans un dossier destination.
 *
 * Exclut automatiquement :
 *  - venv/         → trop lourd, recréé par VenvSetupService
 *  - __pycache__/  → inutile
 *  - Results/      → générés lors des runs
 *  - pabot_results/→ idem
 *  - .git/         → contrôle de version
 *
 * Protège contre le path traversal (zip slip attack).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZipExtractorService {

    private final StorageConfig storageConfig;

    /**
     * Extrait le ZIP dans destDir.
     *
     * @param zipStream  flux du fichier ZIP uploadé
     * @param destDir    dossier de destination (déjà créé)
     * @return nombre de fichiers extraits
     */
    public int extract(InputStream zipStream, Path destDir) throws IOException {
        List<String> excluded = storageConfig.getExcludedDirs();
        int count = 0;

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // Ignorer les dossiers exclus
                if (isExcluded(entryName, excluded)) {
                    log.debug("ZIP : entrée ignorée → {}", entryName);
                    zis.closeEntry();
                    continue;
                }

                Path target = destDir.resolve(entryName).normalize();

                // Sécurité : zip slip
                if (!target.startsWith(destDir)) {
                    throw new SecurityException(
                            "Entrée ZIP non autorisée (path traversal) : " + entryName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    count++;
                    log.debug("ZIP : extrait → {}", entryName);
                }
                zis.closeEntry();
            }
        }

        log.info("ZIP extrait : {} fichiers dans {}", count, destDir);
        return count;
    }

    /**
     * Vérifie si une entrée ZIP appartient à un dossier exclu.
     * Ex : "eforex_im_gc_qa/venv/lib/python3.11/..." → exclu car contient "venv"
     */
    private boolean isExcluded(String entryName, List<String> excludedDirs) {
        String normalized = entryName.replace("\\", "/");
        for (String excluded : excludedDirs) {
            String dir = excluded.trim();
            // Correspondance exacte de segment de chemin
            if (normalized.startsWith(dir + "/")
                    || normalized.contains("/" + dir + "/")
                    || normalized.equals(dir)) {
                return true;
            }
        }
        return false;
    }
}