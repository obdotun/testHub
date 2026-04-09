package com.testhub.repository;

import com.testhub.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByRobotFileIdOrderByPosition(Long robotFileId);

    @Query("SELECT tc FROM TestCase tc WHERE tc.robotFile.project.id = :projectId ORDER BY tc.robotFile.relativePath, tc.position")
    List<TestCase> findAllByProjectId(Long projectId);

    @Query("SELECT COUNT(tc) FROM TestCase tc WHERE tc.robotFile.project.id = :projectId")
    long countByProjectId(Long projectId);
}