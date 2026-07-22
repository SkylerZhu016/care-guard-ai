package com.example.medsim;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByUsername(String username);
    Optional<UserAccount> findFirstByRole(Role role);
}
interface VisitRepository extends JpaRepository<Visit, UUID> {
    List<Visit> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    List<Visit> findByStatusOrderBySubmittedAtAsc(VisitStatus status);
    Optional<Visit> findByIdempotencyKey(String key);
}
interface SymptomRepository extends JpaRepository<SymptomEntity, UUID> { List<SymptomEntity> findByVisitId(UUID visitId); void deleteByVisitId(UUID visitId); }
interface TriageRepository extends JpaRepository<TriageResult, UUID> { Optional<TriageResult> findByVisitId(UUID visitId); }
interface AgentRunRepository extends JpaRepository<AgentRun, UUID> { List<AgentRun> findByVisitIdOrderByCreatedAtDesc(UUID visitId); }
interface CitationRepository extends JpaRepository<CitationEntity, UUID> { List<CitationEntity> findByAgentRunId(UUID runId); }
interface FollowupPlanRepository extends JpaRepository<FollowupPlan, UUID> { List<FollowupPlan> findByOwnerId(UUID ownerId); }
interface FollowupTaskRepository extends JpaRepository<FollowupTask, UUID> { List<FollowupTask> findByPlanId(UUID planId); List<FollowupTask> findByAssigneeIdOrderByDueAtAsc(UUID assigneeId); }
interface SafetyAlertRepository extends JpaRepository<SafetyAlert, UUID> { List<SafetyAlert> findAllByOrderByCreatedAtDesc(); }
interface AuditRepository extends JpaRepository<AuditLog, UUID> { List<AuditLog> findTop100ByOrderByCreatedAtDesc(); }

