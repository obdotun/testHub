package com.testhub.service;

import com.testhub.entity.RobotFile;
import com.testhub.entity.TestCase;
import com.testhub.entity.TestProject;
import com.testhub.repository.RobotFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Scanne le dossier d'un projet après extraction du ZIP,
 * et persiste RobotFile + TestCase en base de données.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectIndexerService {

    private final RobotFileRepository robotFileRepository;
    private final RobotFileParser     parser;

    /**
     * Indexe tous les fichiers .robot trouvés dans projectPath.
     * Supprime les anciens enregistrements du projet avant de réindexer.
     */
    @Transactional
    public int indexProject(TestProject project) throws IOException {
        Path projectPath = Path.of(project.getStoragePath());

        // Supprimer l'index précédent (re-upload)
        robotFileRepository.deleteAll(
                robotFileRepository.findByProjectIdOrderByRelativePath(project.getId()));

        List<RobotFile> indexed = new ArrayList<>();

        Files.walkFileTree(projectPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {

                if (!file.getFileName().toString().endsWith(".robot")) {
                    return FileVisitResult.CONTINUE;
                }

                // Ignorer les fichiers hors du dossier Tests (Resources, Libraries…)
                String relative = projectPath.relativize(file).toString()
                        .replace("\\", "/");

                if (!relative.startsWith(project.getTestsDir() + "/")
                        && !relative.equals(project.getTestsDir())) {
                    return FileVisitResult.CONTINUE;
                }

                List<RobotFileParser.ParsedTestCase> cases = parser.parse(file);

                RobotFile rf = RobotFile.builder()
                        .project(project)
                        .relativePath(relative)
                        .suiteName(parser.suiteName(file))
                        .sizeBytes(attrs.size())
                        .build();

                List<TestCase> testCases = new ArrayList<>();
                for (RobotFileParser.ParsedTestCase tc : cases) {
                    testCases.add(TestCase.builder()
                            .robotFile(rf)
                            .name(tc.name())
                            .position(tc.position())
                            .tags(tc.tags())
                            .build());
                }
                rf.setTestCases(testCases);
                indexed.add(rf);

                log.debug("Indexé : {} ({} tests)", relative, cases.size());
                return FileVisitResult.CONTINUE;
            }
        });

        robotFileRepository.saveAll(indexed);
        int total = indexed.stream().mapToInt(rf -> rf.getTestCases().size()).sum();
        log.info("Projet {} : {} fichiers, {} test cases indexés",
                project.getName(), indexed.size(), total);
        return indexed.size();
    }
}