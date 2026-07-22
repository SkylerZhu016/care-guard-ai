package com.medical.repository;

import com.medical.entity.FollowupPlan;
import com.medical.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FollowupPlanRepository extends JpaRepository<FollowupPlan, Long> {
    List<FollowupPlan> findByVisitId(Long visitId);
    List<FollowupPlan> findByStatus(FollowupPlan.PlanStatus status);
}
