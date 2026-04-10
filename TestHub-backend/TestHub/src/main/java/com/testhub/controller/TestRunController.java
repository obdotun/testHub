package com.testhub.controller;

import com.testhub.dto.LogMessage;
import com.testhub.dto.TestRunDto;
import com.testhub.entity.TestRun;
import com.testhub.repository.RunLogRepository;
import com.testhub.repository.TestRunRepository;
import com.testhub.service.ExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
public class TestRunController {

    private final ExecutionService  executionService;
    private final TestRunRepository runRepository;
    private final RunLogRepository  runLogRepository; // ← injection correcte ✅

    /**
     * POST /api/runs
     * Lance une exécution. Retourne immédiatement avec status=PENDING.
     */
    @PostMapping
    public ResponseEntity<TestRunDto.Response> launch(
            @Valid @RequestBody TestRunDto.LaunchRequest req) {
        return ResponseEntity.ok(executionService.launchRun(req));
    }

    /**
     * GET /api/runs
     * Historique global de tous les runs (tous projets confondus).
     */
    @GetMapping
    public ResponseEntity<List<TestRunDto.Response>> findAll() {
        List<TestRunDto.Response> runs = runRepository.findAllOrderByStartedAtDesc()
                .stream()
                .map(executionService::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(runs);
    }

    /**
     * GET /api/runs/{id}
     * Détail d'un run (statut, résultats, durée…).
     */
    @GetMapping("/{id}")
    public ResponseEntity<TestRunDto.Response> findById(@PathVariable Long id) {
        TestRun run = runRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Run introuvable : id=" + id));
        return ResponseEntity.ok(executionService.toResponse(run));
    }

    /**
     * GET /api/runs/{id}/logs
     * Retourne les logs persistés d'un run.
     * Appelé au montage de RunDetail pour afficher les logs même après la fin du run.
     */
    @GetMapping("/{id}/logs")
    public ResponseEntity<List<LogMessage>> getLogs(@PathVariable Long id) {
        List<LogMessage> logs = runLogRepository.findByRunIdOrderByIdAsc(id)
                .stream()
                .map(l -> LogMessage.builder()
                        .sourceId(id)
                        .text(l.getText())
                        .level(l.getLevel())
                        .timestamp(l.getTimestamp())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(logs);
    }

    /**
     * GET /api/runs/by-project/{projectId}
     * Historique des runs d'un projet spécifique.
     */
    @GetMapping("/by-project/{projectId}")
    public ResponseEntity<List<TestRunDto.Response>> findByProject(
            @PathVariable Long projectId) {
        List<TestRunDto.Response> runs = runRepository
                .findByProjectIdOrderByStartedAtDesc(projectId)
                .stream()
                .map(executionService::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(runs);
    }
}