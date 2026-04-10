package com.testhub.repository;

import com.testhub.entity.SetupLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SetupLogRepository extends JpaRepository<SetupLog, Long> {

    /** Logs du dernier setup (ordre d'insertion) */
    List<SetupLog> findByProjectIdOrderByIdAsc(Long projectId);

    /** Supprime les anciens logs avant un nouveau setup */
    @Modifying
    @Transactional
    @Query("DELETE FROM SetupLog l WHERE l.project.id = :projectId")
    void deleteByProjectId(Long projectId);
}