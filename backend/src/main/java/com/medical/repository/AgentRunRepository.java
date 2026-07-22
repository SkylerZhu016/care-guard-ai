package com.medical.repository;

import com.medical.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {
}
