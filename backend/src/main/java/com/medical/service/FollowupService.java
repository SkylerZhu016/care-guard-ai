package com.medical.service;

import com.medical.entity.*;
import com.medical.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class FollowupService {

    private final FollowupPlanRepository planRepository;
    private final FollowupTaskRepository taskRepository;
    private final VisitRepository visitRepository;

    public FollowupService(FollowupPlanRepository planRepository,
                           FollowupTaskRepository taskRepository,
                           VisitRepository visitRepository) {
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.visitRepository = visitRepository;
    }

    @Transactional
    public FollowupPlan createPlan(Long visitId, String name, String desc, int intervalDays, int totalTimes) {
        var visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new RuntimeException("Visit not found"));

        var plan = new FollowupPlan();
        plan.setVisit(visit);
        plan.setPlanName(name);
        plan.setDescription(desc);
        plan.setIntervalDays(intervalDays);
        plan.setTotalTimes(totalTimes);
        plan.setStatus(FollowupPlan.PlanStatus.ACTIVE);
        plan = planRepository.save(plan);

        for (int i = 1; i <= totalTimes; i++) {
            var task = new FollowupTask();
            task.setPlan(plan);
            task.setDueDate(LocalDate.now().plusDays(intervalDays * i));
            task.setStatus(FollowupTask.TaskStatus.PENDING);
            task.setQuestionnaire("Please describe your recent condition, symptom changes and medication.");
            taskRepository.save(task);
        }

        visit.setStatus(Visit.VisitStatus.FOLLOWUP);
        visitRepository.save(visit);
        return plan;
    }

    public List<FollowupPlan> getPlansByVisit(Long visitId) {
        return planRepository.findByVisitId(visitId);
    }

    public List<FollowupTask> getTasksByPlan(Long planId) {
        return taskRepository.findByPlanId(planId);
    }

    public List<FollowupTask> getOverdueTasks(Long assigneeId) {
        return taskRepository.findByAssigneeIdAndDueDateBeforeAndStatus(
                assigneeId, LocalDate.now(), FollowupTask.TaskStatus.PENDING);
    }

    @Transactional
    public FollowupTask completeTask(Long taskId, String response) {
        var task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Followup task not found"));
        task.setPatientResponse(response);
        task.setStatus(FollowupTask.TaskStatus.COMPLETED);
        return taskRepository.save(task);
    }
}