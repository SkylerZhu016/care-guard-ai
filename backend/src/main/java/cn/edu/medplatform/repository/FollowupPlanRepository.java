package cn.edu.medplatform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import cn.edu.medplatform.entity.*;
import java.util.List;

public interface FollowupPlanRepository extends JpaRepository<FollowupPlan, Long> {
    Page<FollowupPlan> findByPatientId(Long patientId, Pageable p);
}
