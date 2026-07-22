package com.medical.repository;

import com.medical.entity.FollowupTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface FollowupTaskRepository extends JpaRepository<FollowupTask, Long> {
    List<FollowupTask> findByAssigneeIdAndDueDateBeforeAndStatus(
        Long assigneeId, LocalDate date, FollowupTask.TaskStatus status);
    List<FollowupTask> findByPlanId(Long planId);
    long countByStatus(FollowupTask.TaskStatus status);
}
