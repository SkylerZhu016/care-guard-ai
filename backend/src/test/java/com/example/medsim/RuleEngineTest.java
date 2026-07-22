package com.example.medsim;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {
    private final RuleEngine engine = new RuleEngine();

    @Test void chestPainWithDyspneaIsEmergency() {
        var result = engine.evaluate(List.of(
            new SymptomInput("CHEST_PAIN", "胸痛", 8, "1小时"),
            new SymptomInput("DYSPNEA", "呼吸困难", 6, "30分钟")
        ));
        assertThat(result.urgency()).isEqualTo(Urgency.EMERGENCY);
        assertThat(result.reasonCodes()).contains("CHEST_PAIN_WITH_RED_FLAG");
    }

    @Test void highSeverityNeverBecomesRoutine() {
        var result = engine.evaluate(List.of(new SymptomInput("HEADACHE", "头痛", 8, "今天")));
        assertThat(result.urgency()).isEqualTo(Urgency.URGENT);
    }

    @Test void stateMachineRejectsSkippingReview() {
        assertThat(engine.canTransition(VisitStatus.PROCESSING, VisitStatus.REVIEWED)).isFalse();
        assertThat(engine.canTransition(VisitStatus.PROCESSING, VisitStatus.PENDING_REVIEW)).isTrue();
        assertThat(engine.canTransition(VisitStatus.CLOSED, VisitStatus.DRAFT)).isFalse();
    }

    @Test void maxUrgencyIsMonotonic() {
        assertThat(engine.max(Urgency.EMERGENCY, Urgency.ROUTINE)).isEqualTo(Urgency.EMERGENCY);
    }
}

