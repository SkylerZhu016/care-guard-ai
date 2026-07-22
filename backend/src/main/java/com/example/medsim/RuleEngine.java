package com.example.medsim;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
class RuleEngine {
    RuleOutcome evaluate(List<SymptomInput> symptoms) {
        Set<String> codes = new HashSet<>();
        int maxSeverity = 0;
        for (var symptom : symptoms) { codes.add(symptom.code()); maxSeverity = Math.max(maxSeverity, symptom.severity()); }
        List<String> reasons = new ArrayList<>();
        Urgency urgency = Urgency.ROUTINE;
        if (codes.contains("ALTERED_CONSCIOUSNESS")) {
            urgency = Urgency.EMERGENCY; reasons.add("ALTERED_CONSCIOUSNESS_RED_FLAG");
        }
        if (codes.contains("CHEST_PAIN") && (codes.contains("DYSPNEA") || codes.contains("SYNCOPE") || maxSeverity >= 8)) {
            urgency = Urgency.EMERGENCY; reasons.add("CHEST_PAIN_WITH_RED_FLAG");
        } else if (codes.contains("DYSPNEA") || maxSeverity >= 7) {
            urgency = max(urgency, Urgency.URGENT); reasons.add("SEVERE_OR_RESPIRATORY_SYMPTOM");
        }
        if (reasons.isEmpty()) reasons.add("NO_CONFIGURED_RED_FLAG");
        return new RuleOutcome(urgency, List.copyOf(reasons));
    }

    Urgency max(Urgency first, Urgency second) { return first.ordinal() >= second.ordinal() ? first : second; }
    boolean canTransition(VisitStatus current, VisitStatus target) {
        return switch (current) {
            case DRAFT -> target == VisitStatus.SUBMITTED;
            case SUBMITTED -> target == VisitStatus.PROCESSING || target == VisitStatus.REJECTED;
            case PROCESSING -> target == VisitStatus.PENDING_REVIEW;
            case PENDING_REVIEW -> target == VisitStatus.REVIEWED || target == VisitStatus.REJECTED;
            case REVIEWED -> target == VisitStatus.FOLLOWUP_ACTIVE || target == VisitStatus.CLOSED;
            case FOLLOWUP_ACTIVE -> target == VisitStatus.CLOSED;
            default -> false;
        };
    }
}

