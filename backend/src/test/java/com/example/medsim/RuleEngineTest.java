package com.example.medsim;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {
    private final RuleEngine engine = new RuleEngine(new ObjectMapper());

    @Test void chestPainWithDyspneaIsEmergency() {
        var result = engine.evaluateV2(List.of(report("CHEST_PAIN", SupportLevel.RULE_SUPPORTED, "chest.current"),
            report("DYSPNEA", SupportLevel.RULE_SUPPORTED, "dyspnea.current")));
        assertThat(result.urgency()).isEqualTo(Urgency.EMERGENCY);
        assertThat(result.reasonCodes()).contains("CHEST_PAIN_WITH_DYSPNEA");
        assertThat(result.coverageStatus()).isEqualTo(CoverageStatus.FULL);
    }

    @Test void chestPainWithSyncopeIsEmergency() {
        var result = engine.evaluateV2(List.of(report("CHEST_PAIN", SupportLevel.RULE_SUPPORTED, "chest.current"),
            report("SYNCOPE", SupportLevel.RULE_SUPPORTED, "syncope.loss_of_consciousness")));
        assertThat(result.urgency()).isEqualTo(Urgency.EMERGENCY);
        assertThat(result.reasonCodes()).contains("CHEST_PAIN_WITH_SYNCOPE");
    }

    @Test void alteredConsciousnessIsEmergency() {
        var result = engine.evaluateV2(List.of(report("ALTERED_CONSCIOUSNESS", SupportLevel.RULE_SUPPORTED, "consciousness.present")));
        assertThat(result.urgency()).isEqualTo(Urgency.EMERGENCY);
    }

    @Test void dyspneaAloneIsUrgent() {
        var result = engine.evaluateV2(List.of(report("DYSPNEA", SupportLevel.RULE_SUPPORTED, "dyspnea.current")));
        assertThat(result.urgency()).isEqualTo(Urgency.URGENT);
    }

    @Test void unsupportedSymptomNeverSilentlyBecomesRoutine() {
        var result = engine.evaluateV2(List.of(report("HEADACHE", SupportLevel.RECORD_ONLY, null)));
        assertThat(result.urgency()).isNull();
        assertThat(result.coverageStatus()).isEqualTo(CoverageStatus.NONE);
        assertThat(result.assessmentStatus()).isEqualTo(AssessmentStatus.REQUIRES_MANUAL_REVIEW);
        assertThat(result.reasonCodes()).contains("UNSUPPORTED_SYMPTOMS_REQUIRE_REVIEW");
    }

    @Test void mixedSymptomsKeepEmergencyAndRequireReview() {
        var result = engine.evaluateV2(List.of(report("ALTERED_CONSCIOUSNESS", SupportLevel.RULE_SUPPORTED, "consciousness.present"),
            report("ABDOMINAL_PAIN", SupportLevel.RECORD_ONLY, null)));
        assertThat(result.urgency()).isEqualTo(Urgency.EMERGENCY);
        assertThat(result.coverageStatus()).isEqualTo(CoverageStatus.PARTIAL);
        assertThat(result.assessmentStatus()).isEqualTo(AssessmentStatus.REQUIRES_MANUAL_REVIEW);
    }

    @Test void unknownRuleAnswerDoesNotBecomePositive() {
        var value = report("SYNCOPE", SupportLevel.RULE_SUPPORTED, null);
        value.answersJson = "[{\"questionId\":\"syncope.loss_of_consciousness\",\"selectedOptions\":[\"UNKNOWN\"]}]";
        var result = engine.evaluateV2(List.of(value));
        assertThat(result.urgency()).isNull();
        assertThat(result.coverageStatus()).isEqualTo(CoverageStatus.FULL);
        assertThat(result.assessmentStatus()).isEqualTo(AssessmentStatus.REQUIRES_MANUAL_REVIEW);
    }

    @Test void stateMachineRejectsSkippingReview() {
        assertThat(engine.canTransition(VisitStatus.PROCESSING, VisitStatus.REVIEWED)).isFalse();
        assertThat(engine.canTransition(VisitStatus.PROCESSING, VisitStatus.PENDING_REVIEW)).isTrue();
        assertThat(engine.canTransition(VisitStatus.CLOSED, VisitStatus.DRAFT)).isFalse();
    }

    private SymptomEntity report(String code, SupportLevel supportLevel, String yesQuestion) {
        var value = new SymptomEntity(); value.code = code; value.supportLevel = supportLevel; value.reportSource = "CATALOG";
        value.answersJson = yesQuestion == null ? "[]" : "[{\"questionId\":\"" + yesQuestion + "\",\"selectedOptions\":[\"YES\"]}]";
        return value;
    }
}
