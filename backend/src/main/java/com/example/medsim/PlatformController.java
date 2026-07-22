package com.example.medsim;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
class PlatformController {
    private final PlatformService service;
    PlatformController(PlatformService service){this.service=service;}
    private AuthPrincipal actor(Authentication auth){return (AuthPrincipal)auth.getPrincipal();}

    @PostMapping("/visits") @PreAuthorize("hasRole('SIMULATED_PATIENT')") VisitView create(Authentication auth,@Valid @RequestBody VisitInput body){return service.createVisit(actor(auth),body);}
    @PutMapping("/visits/{id}/intake") @PreAuthorize("hasRole('SIMULATED_PATIENT')") VisitView update(Authentication auth,@PathVariable UUID id,@Valid @RequestBody VisitInput body){return service.updateIntake(actor(auth),id,body);}
    @PostMapping("/visits/{id}/submit") @PreAuthorize("hasRole('SIMULATED_PATIENT')") VisitView submit(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String key){return service.submit(actor(auth),id,key);}
    @GetMapping("/visits/mine") @PreAuthorize("hasRole('SIMULATED_PATIENT')") List<VisitView> mine(Authentication auth){return service.myVisits(actor(auth));}
    @GetMapping("/visits/{id}") VisitView visit(Authentication auth,@PathVariable UUID id){return service.getVisit(actor(auth),id);}

    @GetMapping("/clinician/visits") @PreAuthorize("hasRole('CLINICIAN')") List<VisitView> queue(){return service.clinicianQueue();}
    @PostMapping("/triage-results/{id}/review") @PreAuthorize("hasRole('CLINICIAN')") VisitView review(Authentication auth,@PathVariable UUID id,@Valid @RequestBody ReviewInput body){return service.review(actor(auth),id,body);}
    @PostMapping("/followup-plans") @PreAuthorize("hasRole('CLINICIAN')") PlanView createPlan(Authentication auth,@Valid @RequestBody PlanInput body){return service.createPlan(actor(auth),body);}
    @PostMapping("/followup-plans/{id}/activate") @PreAuthorize("hasRole('CLINICIAN')") PlanView activate(Authentication auth,@PathVariable UUID id){return service.activatePlan(actor(auth),id);}

    @GetMapping("/followup-tasks/mine") @PreAuthorize("hasAnyRole('FOLLOWUP_STAFF','SIMULATED_PATIENT')") List<TaskView> tasks(Authentication auth){return service.assignedTasks(actor(auth));}
    @PatchMapping("/followup-tasks/{id}") @PreAuthorize("hasRole('FOLLOWUP_STAFF')") TaskView updateTask(Authentication auth,@PathVariable UUID id,@Valid @RequestBody TaskUpdate body){return service.updateTask(actor(auth),id,body);}

    @GetMapping("/admin/safety-alerts") @PreAuthorize("hasRole('ADMIN')") List<AlertView> alerts(){return service.listAlerts();}
    @GetMapping("/admin/audit-logs") @PreAuthorize("hasRole('ADMIN')") List<AuditView> audits(){return service.listAudits();}
    @GetMapping("/admin/agent-runs") @PreAuthorize("hasRole('ADMIN')") List<AgentRunView> runs(){return service.listRuns();}
    @GetMapping("/admin/guidelines") @PreAuthorize("hasRole('ADMIN')") List<GuidelineView> guidelines(){return service.listGuidelines();}
    @PostMapping("/admin/guidelines/reindex") @PreAuthorize("hasRole('ADMIN')") void reindex(Authentication auth){service.reindexGuidelines(actor(auth));}
    @PostMapping("/admin/guidelines/{guidelineId}/versions/{versionId}/activate") @PreAuthorize("hasRole('ADMIN')") void activateGuideline(Authentication auth,@PathVariable String guidelineId,@PathVariable String versionId){service.activateGuideline(actor(auth),guidelineId,versionId);}
}

