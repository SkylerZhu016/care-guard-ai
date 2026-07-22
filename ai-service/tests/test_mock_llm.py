"""Mock LLM 与 Agent 测试（离线，无需 DB）"""
import json
import pytest
from app.clients.llm import MockLLM, extract_json, detect_injection
from app.agents import run_structure, run_retrieval, run_risk, run_safety, run_summary, _form_to_text


class TestMockLLM:
    def test_structure_output_valid_json(self):
        text = "胸痛伴呼吸困难2小时，出汗"
        raw, tokens = MockLLM.generate("你是医疗信息结构化助手", text)
        data = extract_json(raw)
        assert data["schema_version"] == "1.0"
        assert data["chief_complaint"]
        assert isinstance(data["symptoms"], list)
        assert len(data["symptoms"]) > 0
        # 应识别到胸痛和呼吸困难
        names = [s["name"] for s in data["symptoms"]]
        assert "胸痛" in names
        assert "呼吸困难" in names

    def test_structure_confidence_range(self):
        raw, _ = MockLLM.generate("结构化", "头痛三天")
        data = extract_json(raw)
        assert 0.0 <= data["confidence"] <= 1.0

    def test_retrieval_queries(self):
        raw, _ = MockLLM.generate("根据结构化症状生成检索词", json.dumps({"chief_complaint": "胸痛"}))
        data = extract_json(raw)
        assert isinstance(data["queries"], list)
        assert len(data["queries"]) >= 1

    def test_risk_output(self):
        context = json.dumps({"chief_complaint": "胸痛伴呼吸困难", "accompanying": ["出汗"], "severity": "SEVERE"})
        raw, _ = MockLLM.generate("风险分析", context)
        data = extract_json(raw)
        assert data["risk_level"] in ("LOW", "MEDIUM", "HIGH", "CRITICAL")
        assert isinstance(data["risk_points"], list)

    def test_safety_pass(self):
        raw, _ = MockLLM.generate("安全审查", "some content")
        data = extract_json(raw)
        assert data["verdict"] in ("PASS", "FAIL")

    def test_summary_has_disclaimer(self):
        raw, _ = MockLLM.generate("汇总摘要", json.dumps({"chief_complaint": "头痛"}))
        data = extract_json(raw)
        assert "不能替代医生诊断" in data["disclaimer"]


class TestAgents:
    def test_run_structure(self):
        form = {"chiefComplaint": "发热咳嗽三天", "severity": "MODERATE", "accompanying": ["乏力"]}
        result, tokens = run_structure(form)
        assert "chief_complaint" in result
        assert tokens > 0

    def test_run_retrieval(self):
        structured = {"chief_complaint": "胸痛", "symptoms": [{"name": "胸痛"}]}
        queries, tokens = run_retrieval(structured)
        assert isinstance(queries, list)
        assert len(queries) >= 1

    def test_run_risk(self):
        structured = {"chief_complaint": "胸痛伴呼吸困难", "accompanying": ["出汗"], "severity": "SEVERE"}
        rule_hits = [{"rule_code": "RF001", "rule_name": "胸痛伴呼吸困难", "risk_level": "CRITICAL", "message": "胸痛伴呼吸困难"}]
        citations = [{"chunk_id": 1, "title": "胸痛鉴别", "section": "第一章"}]
        result, tokens = run_risk(structured, rule_hits, citations)
        assert result["risk_level"] in ("LOW", "MEDIUM", "HIGH", "CRITICAL")

    def test_run_safety_pass(self):
        content = json.dumps({"risk_level": "LOW", "risk_summary": "低风险", "risk_points": []})
        result, _ = run_safety(content, [], [], True)
        assert result["verdict"] in ("PASS", "FAIL")

    def test_run_safety_fail_diagnosis(self):
        content = "确诊为感冒。不能替代医生诊断。"
        result, _ = run_safety(content, [{"point": "test", "severity": "LOW"}], [], True)
        assert result["verdict"] == "FAIL"

    def test_run_summary(self):
        structured = {"chief_complaint": "头痛", "symptoms": [{"name": "头痛"}]}
        risk = {"risk_level": "LOW", "risk_points": []}
        result, _ = run_summary(structured, risk, [])
        assert "不能替代医生诊断" in result["disclaimer"]


class TestFormToText:
    def test_basic(self):
        form = {"chiefComplaint": "头痛", "onsetTime": "2026-07-01", "severity": "MODERATE", "accompanying": ["恶心"]}
        text = _form_to_text(form)
        assert "头痛" in text
        assert "2026-07-01" in text
        assert "恶心" in text

    def test_empty(self):
        assert _form_to_text({}) == ""

    def test_special_group(self):
        form = {"chiefComplaint": "腹痛", "basic": {"specialGroup": "PREGNANT"}}
        text = _form_to_text(form)
        assert "PREGNANT" in text
