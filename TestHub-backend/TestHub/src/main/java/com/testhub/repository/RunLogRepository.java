package com.testhub.repository;

import com.testhub.entity.RunLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunLogRepository extends JpaRepository<RunLog, Long> {
    List<RunLog> findByRunIdOrderById(Long runId);
}
