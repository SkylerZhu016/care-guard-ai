package cn.edu.medplatform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.edu.medplatform.entity.*;
import java.util.List;

public interface VisitRepository extends JpaRepository<Visit, Long> {
    Page<Visit> findByOwnerUserId(Long ownerUserId, Pageable p);
    @Query("SELECT v FROM Visit v WHERE (:status IS NULL OR v.status = :status) AND (:riskLevel IS NULL OR v.riskLevel = :riskLevel) AND (:patientId IS NULL OR v.patientId = :patientId) AND (:ownerUserId IS NULL OR v.ownerUserId = :ownerUserId)")
    Page<Visit> search(@Param("status") String status, @Param("riskLevel") String riskLevel, @Param("patientId") Long patientId, @Param("ownerUserId") Long ownerUserId, Pageable p);
    @Query("SELECT v FROM Visit v WHERE v.status IN ('PENDING_REVIEW','NEED_INFO') AND (:riskLevel IS NULL OR v.riskLevel = :riskLevel) AND (:status IS NULL OR v.status = :status) ORDER BY CASE WHEN v.riskLevel='CRITICAL' THEN 0 WHEN v.riskLevel='HIGH' THEN 1 WHEN v.riskLevel='MEDIUM' THEN 2 ELSE 3 END, v.submittedAt ASC")
    Page<Visit> reviewQueue(@Param("riskLevel") String riskLevel, @Param("status") String status, Pageable p);
    List<Visit> findByOwnerUserIdAndStatus(Long ownerUserId, String status);
}
