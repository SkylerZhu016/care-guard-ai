package cn.edu.medplatform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.edu.medplatform.entity.*;
import java.util.List;

public interface FollowupTaskRepository extends JpaRepository<FollowupTask, Long> {
    @Query("SELECT t FROM FollowupTask t WHERE (:status IS NULL OR t.status = :status) AND (:riskLevel IS NULL OR t.riskLevel = :riskLevel) AND (:patientId IS NULL OR t.patientId = :patientId) AND (:assigneeId IS NULL OR t.assigneeId = :assigneeId) AND (:dueBefore IS NULL OR t.dueDate <= :dueBefore) ORDER BY t.dueDate ASC")
    Page<FollowupTask> search(@Param("status") String status, @Param("riskLevel") String riskLevel, @Param("patientId") Long patientId, @Param("assigneeId") Long assigneeId, @Param("dueBefore") java.time.LocalDate dueBefore, Pageable p);
    List<FollowupTask> findByPlanId(Long planId);
}
