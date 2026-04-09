package com.testhub.repository;

import com.testhub.entity.TestRun;
import com.testhub.enums.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestRunRepository extends JpaRepository<TestRun, Long> {

    List<TestRun> findByProjectIdOrderByStartedAtDesc(Long projectId);

    @Query("SELECT r FROM TestRun r ORDER BY r.startedAt DESC")
    List<TestRun> findAllOrderByStartedAtDesc();

    @Query("SELECT COUNT(r) FROM TestRun r WHERE r.project.id = :projectId AND r.status = :status")
    long countByProjectIdAndStatus(Long projectId, RunStatus status);
}