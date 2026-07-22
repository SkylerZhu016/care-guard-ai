package com.example.medsim;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
class RuleEngine {
    private final ObjectMapper mapper;
    RuleEngine(ObjectMapper mapper) { this.mapper = mapper; }

    RuleOutcome evaluate(List<SymptomInput> symptoms) {
        Set<String> codes = new HashSet<>();
        for (var symptom : symptoms) codes.add(symptom.code());
        return evaluateCodes(codes, codes.stream().anyMatch(code -> !supported(code)), codes.stream().anyMatch(this::supported));
    }

    RuleOutcome evaluateV2(List<SymptomEntity> symptoms) {
        Set<String> codes = new HashSet<>();
        boolean hasUnsupported = false;
        boolean hasSupported = false;
        for (var symptom : symptoms) {
            if (isRulePresent(symptom)) codes.add(symptom.code);
            hasUnsupported |= symptom.supportLevel != SupportLevel.RULE_SUPPORTED;
            hasSupported |= symptom.supportLevel == SupportLevel.RULE_SUPPORTED;
        }
        return evaluateCodes(codes, hasUnsupported, hasSupported);
    }

    private RuleOutcome evaluateCodes(Set<String> codes, boolean hasUnsupported, boolean hasSupported) {
        CoverageStatus coverage = hasSupported ? (hasUnsupported ? CoverageStatus.PARTIAL : CoverageStatus.FULL) : CoverageStatus.NONE;
        List<String> reasons = new ArrayList<>();
        Urgency urgency = null;
        if (codes.contains("ALTERED_CONSCIOUSNESS")) {
            urgency = Urgency.EMERGENCY; reasons.add("ALTERED_CONSCIOUSNESS_PRESENT");
        } else if (codes.contains("CHEST_PAIN") && codes.contains("DYSPNEA")) {
            urgency = Urgency.EMERGENCY; reasons.add("CHEST_PAIN_WITH_DYSPNEA");
        } else if (codes.contains("CHEST_PAIN") && codes.contains("SYNCOPE")) {
            urgency = Urgency.EMERGENCY; reasons.add("CHEST_PAIN_WITH_SYNCOPE");
        } else if (codes.contains("DYSPNEA")) {
            urgency = Urgency.URGENT; reasons.add("DYSPNEA_PRESENT");
        }
        if (hasUnsupported) reasons.add("UNSUPPORTED_SYMPTOMS_REQUIRE_REVIEW");
        if (urgency == null && hasSupported) reasons.add("SUPPORTED_NO_RULE_MATCH_REQUIRES_REVIEW");
        AssessmentStatus assessment = coverage == CoverageStatus.FULL && urgency != null
            ? AssessmentStatus.RULE_EVALUATED : AssessmentStatus.REQUIRES_MANUAL_REVIEW;
        return new RuleOutcome(urgency, List.copyOf(reasons), coverage, assessment);
    }

    private boolean supported(String code) {
        return Set.of("CHEST_PAIN", "DYSPNEA", "SYNCOPE", "ALTERED_CONSCIOUSNESS").contains(code);
    }

    private boolean isRulePresent(SymptomEntity symptom) {
        if (symptom.supportLevel != SupportLevel.RULE_SUPPORTED) return false;
        if ("RELATED_ANSWER".equals(symptom.reportSource)) return true;
        String questionId = switch (symptom.code) {
            case "CHEST_PAIN" -> "chest.current";
            case "DYSPNEA" -> "dyspnea.current";
            case "SYNCOPE" -> "syncope.loss_of_consciousness";
            case "ALTERED_CONSCIOUSNESS" -> "consciousness.present";
            default -> null;
        };
        if (questionId == null || symptom.answersJson == null) return false;
        try {
            for (var answer : mapper.readTree(symptom.answersJson)) {
                if (questionId.equals(answer.path("questionId").asText())) {
                    for (var option : answer.path("selectedOptions")) if ("YES".equals(option.asText())) return true;
                    return false;
                }
            }
        } catch (Exception ignored) { return false; }
        return false;
    }

    Urgency max(Urgency first, Urgency second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.ordinal() >= second.ordinal() ? first : second;
    }
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

