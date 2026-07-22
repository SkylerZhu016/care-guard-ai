package cn.edu.medplatform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.edu.medplatform.entity.*;
import java.util.List;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {
    List<AgentRun> findByVisitIdOrderByCreatedAtDesc(Long visitId);
    @Query("SELECT r FROM AgentRun r WHERE (:status IS NULL OR r.status = :status) AND (:visitId IS NULL OR r.visitId = :visitId) ORDER BY r.createdAt DESC")
    Page<AgentRun> search(@Param("status") String status, @Param("visitId") Long visitId, Pageable p);
}
