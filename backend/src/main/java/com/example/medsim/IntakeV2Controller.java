package com.example.medsim;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2")
class IntakeV2Controller {
    private final IntakeV2Service service;
    IntakeV2Controller(IntakeV2Service service) { this.service = service; }
    private AuthPrincipal actor(Authentication authentication) { return (AuthPrincipal) authentication.getPrincipal(); }

    @GetMapping("/intake-catalog")
    IntakeCatalogView catalog() { return service.catalog(); }

    @GetMapping("/patient-profile") @PreAuthorize("hasRole('PATIENT')")
    PatientProfileView profile(Authentication authentication) { return service.profile(actor(authentication)); }

    @PutMapping("/patient-profile") @PreAuthorize("hasRole('PATIENT')")
    PatientProfileView saveProfile(Authentication authentication, @Valid @RequestBody PatientProfileInput body) {
        return service.saveProfile(actor(authentication), body);
    }

    @PostMapping("/visits") @PreAuthorize("hasRole('PATIENT')")
    VisitViewV2 create(Authentication authentication, @Valid @RequestBody VisitIntakeV2Input body) {
        return service.create(actor(authentication), body);
    }

    @PutMapping("/visits/{id}") @PreAuthorize("hasRole('PATIENT')")
    VisitViewV2 update(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody VisitIntakeV2Input body) {
        return service.update(actor(authentication), id, body);
    }

    @PostMapping("/visits/{id}/submit") @PreAuthorize("hasRole('PATIENT')")
    VisitViewV2 submit(Authentication authentication, @PathVariable UUID id,
                       @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.submit(actor(authentication), id, key);
    }

    @PostMapping("/visits/{id}/analyze-complaint") @PreAuthorize("hasRole('PATIENT')")
    ComplaintAnalysisView analyzeComplaint(Authentication authentication, @PathVariable UUID id) {
        return service.analyzeComplaint(actor(authentication), id);
    }

    @PutMapping("/visits/{id}/complaint-structure") @PreAuthorize("hasRole('PATIENT')")
    ComplaintAnalysisView confirmComplaint(Authentication authentication, @PathVariable UUID id,
                                             @Valid @RequestBody ComplaintConfirmationInput body) {
        return service.confirmComplaint(actor(authentication), id, body);
    }

    @PostMapping("/visits/{id}/supplements") @PreAuthorize("hasRole('PATIENT')")
    VisitSupplementView supplement(Authentication authentication, @PathVariable UUID id,
                                    @Valid @RequestBody VisitSupplementInput body) {
        return service.supplement(actor(authentication), id, body);
    }

    @GetMapping("/visits/mine") @PreAuthorize("hasRole('PATIENT')")
    List<VisitViewV2> mine(Authentication authentication) { return service.mine(actor(authentication)); }

    @GetMapping("/visits/{id}")
    VisitViewV2 visit(Authentication authentication, @PathVariable UUID id) { return service.get(actor(authentication), id); }

    @GetMapping("/clinician/visits") @PreAuthorize("hasRole('CLINICIAN')")
    List<VisitViewV2> queue() { return service.clinicianQueue(); }
}
