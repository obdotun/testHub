package com.testhub.repository;

import com.testhub.entity.RobotFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RobotFileRepository extends JpaRepository<RobotFile, Long> {
    List<RobotFile> findByProjectIdOrderByRelativePath(Long projectId);
    Optional<RobotFile> findByProjectIdAndRelativePath(Long projectId, String relativePath);
    void deleteByProjectIdAndRelativePath(Long projectId, String relativePath);
}