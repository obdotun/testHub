package com.testhub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrait les résultats numériques depuis output.xml généré par Robot Framework.
 *
 * Structure visée dans output.xml :
 *   <statistics>
 *     <total>
 *       <stat pass="5" fail="1" skip="0">All Tests</stat>
 *     </total>
 *   </statistics>
 */
@Slf4j
@Component
public class OutputXmlParser {

    private static final Pattern STAT_PATTERN =
            Pattern.compile("pass=\"(\\d+)\"\\s+fail=\"(\\d+)\"\\s+skip=\"(\\d+)\"");

    // Fallback si skip absent (anciennes versions RF)
    private static final Pattern STAT_NO_SKIP =
            Pattern.compile("pass=\"(\\d+)\"\\s+fail=\"(\\d+)\"");

    public Result parse(Path reportDir) {
        Path outputXml = reportDir.resolve("output.xml");

        if (!Files.exists(outputXml)) {
            log.warn("output.xml introuvable dans {}", reportDir);
            return Result.unknown();
        }

        try {
            String content = Files.readString(outputXml);

            Matcher m = STAT_PATTERN.matcher(content);
            if (m.find()) {
                return new Result(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
            }

            // Fallback : RF sans skip
            m = STAT_NO_SKIP.matcher(content);
            if (m.find()) {
                return new Result(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        0);
            }

        } catch (IOException e) {
            log.error("Erreur lecture output.xml : {}", e.getMessage());
        }

        return Result.unknown();
    }

    public record Result(int passed, int failed, int skipped) {
        public static Result unknown() { return new Result(0, 0, 0); }
        public boolean allPassed()     { return failed == 0 && (passed > 0 || skipped > 0); }
    }
}