package com.testhub.repository;

import com.testhub.entity.TestProject;
import com.testhub.enums.VenvStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface TestProjectRepository extends JpaRepository<TestProject, Long> {
    Optional<TestProject> findByName(String name);
    boolean existsByName(String name);
    List<TestProject> findByVenvStatus(VenvStatus status);
}