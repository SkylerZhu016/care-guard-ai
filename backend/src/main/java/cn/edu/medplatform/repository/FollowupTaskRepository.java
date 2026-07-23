package cn.edu.medplatform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.edu.medplatform.entity.*;
import java.util.List;

public interface FollowupTaskRepository extends JpaRepository<FollowupTask, Long> {
    @Query("SELECT t FROM FollowupTask t WHERE (t.status = :status OR :status = '') AND (t.riskLevel = :riskLevel OR :riskLevel = '') AND (:patientId = -1L OR t.patientId = :patientId) ORDER BY t.dueDate ASC")
    Page<FollowupTask> search(@Param("status") String status, @Param("riskLevel") String riskLevel, @Param("patientId") Long patientId, Pageable p);
    List<FollowupTask> findByPlanId(Long planId);
}
