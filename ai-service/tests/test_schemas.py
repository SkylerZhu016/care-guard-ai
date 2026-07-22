"""Schema 校验测试"""
import json
import pytest
from app.schemas import (
    SymptomExtractionSchema, RiskAnalysisSchema, SafetyReviewSchema,
    ReviewSummarySchema, RiskPoint, SafetyIssue,
)


class TestSymptomExtraction:
    def test_valid(self):
        data = {
            "schema_version": "1.0",
            "chief_complaint": "胸痛伴呼吸困难",
            "symptoms": [{"name": "胸痛", "body_part": "胸部", "severity": "SEVERE", "duration": "2小时"}],
            "missing_fields": [],
            "confidence": 0.9,
        }
        s = SymptomExtractionSchema(**data)
        assert s.chief_complaint == "胸痛伴呼吸困难"
        assert s.confidence == 0.9

    def test_missing_required(self):
        with pytest.raises(Exception):
            SymptomExtractionSchema(chief_complaint="")

    def test_confidence_range(self):
        with pytest.raises(Exception):
            SymptomExtractionSchema(chief_complaint="test", confidence=1.5)

    def test_defaults(self):
        s = SymptomExtractionSchema(chief_complaint="头痛", confidence=0.0)
        assert s.symptoms == []
        assert s.special_group == "NONE"
        assert s.confidence == 0.0


class TestRiskAnalysis:
    def test_valid(self):
        data = {
            "risk_level": "HIGH",
            "risk_summary": "胸痛风险",
            "risk_points": [{"point": "胸痛", "basis": "指南", "citation_ids": ["1"], "severity": "HIGH"}],
        }
        s = RiskAnalysisSchema(**data)
        assert s.risk_level == "HIGH"

    def test_empty_points(self):
        s = RiskAnalysisSchema(risk_level="LOW", risk_summary="低风险")
        assert s.risk_points == []


class TestSafetyReview:
    def test_pass(self):
        s = SafetyReviewSchema(verdict="PASS")
        assert s.verdict == "PASS"

    def test_fail_with_issues(self):
        s = SafetyReviewSchema(verdict="FAIL", issues=[SafetyIssue(type="DIAGNOSIS", detail="确诊", severity="HIGH")])
        assert len(s.issues) == 1


class TestReviewSummary:
    def test_disclaimer_default(self):
        s = ReviewSummarySchema()
        assert "不能替代医生诊断" in s.disclaimer
