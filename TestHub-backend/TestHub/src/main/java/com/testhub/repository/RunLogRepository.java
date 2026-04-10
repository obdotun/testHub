package com.testhub.repository;

import com.testhub.entity.RunLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunLogRepository extends JpaRepository<RunLog, Long> {

    /** Retourne tous les logs d'un run dans l'ordre d'insertion */
    List<RunLog> findByRunIdOrderByIdAsc(Long runId);

    /** Supprime tous les logs d'un run (utile si on relance) */
    @Modifying
    @Query("DELETE FROM RunLog l WHERE l.run.id = :runId")
    void deleteByRunId(Long runId);

    /** Compte les logs d'un run */
    long countByRunId(Long runId);
}