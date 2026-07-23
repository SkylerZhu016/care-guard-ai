import json
from types import SimpleNamespace

import httpx
import pytest

import app.providers as providers
from app.providers import OpenAICompatibleProvider, ProviderTimeoutError
from app.schemas import AnalysisRequest, ComplaintStructureRequest


def request():
    return AnalysisRequest.model_validate({
        "runId": "run-adapter-001", "visitId": "visit-adapter-001", "ageBand": "ADULT",
        "chiefComplaint": "感到乏力", "symptoms": [{"code": "FATIGUE", "name": "乏力", "supportLevel": "RECORD_ONLY"}],
        "freeText": "希望补充信息", "ruleUrgency": None, "ruleReasonCodes": ["UNSUPPORTED_SYMPTOMS_REQUIRE_REVIEW"],
        "coverageStatus": "NONE", "assessmentStatus": "REQUIRES_MANUAL_REVIEW",
    })


def test_openai_compatible_uses_separate_generation_and_critic_calls(monkeypatch):
    monkeypatch.setattr(providers, "settings", SimpleNamespace(base_url="https://provider.example/v1", api_key="test-only", model="model-test"))
    calls = []
    responses = [
        {"caseSummary": "信息摘要", "proposedUrgency": None, "rationale": ["等待人工复核"], "missingQuestions": [], "evidence": []},
        {"violations": [], "review": "独立安全复核通过"},
    ]

    class Response:
        def __init__(self, content): self.content = content
        def raise_for_status(self): return None
        def json(self): return {"choices": [{"message": {"content": json.dumps(self.content, ensure_ascii=False)}}]}

    def post(url, headers, json, timeout):
        calls.append({"url": url, "headers": headers, "payload": json, "timeout": timeout})
        return Response(responses[len(calls) - 1])

    monkeypatch.setattr(providers.httpx, "post", post)
    adapter = OpenAICompatibleProvider()
    draft = adapter.generate(request(), [])
    critic = adapter.critique(request(), draft, [])

    assert len(calls) == 2
    assert calls[0]["url"] == "https://provider.example/v1/chat/completions"
    assert calls[0]["headers"]["Authorization"] == "Bearer test-only"
    assert "只整理患者填写的事实" in calls[0]["payload"]["messages"][0]["content"]
    assert "独立安全审查角色" in calls[1]["payload"]["messages"][0]["content"]
    assert critic["violations"] == []


def test_openai_compatible_maps_timeout(monkeypatch):
    monkeypatch.setattr(providers, "settings", SimpleNamespace(base_url="https://provider.example/v1", api_key="test-only", model="model-test"))

    def timeout(*args, **kwargs):
        raise httpx.TimeoutException("test timeout")

    monkeypatch.setattr(providers.httpx, "post", timeout)
    with pytest.raises(ProviderTimeoutError, match="AI_TIMEOUT"):
        OpenAICompatibleProvider().generate(request(), [])


def test_openai_compatible_extracts_json_from_model_chatter():
    content = '好的，以下是我的回答：\n```json\n{"extractedTags":[{"code":"ABDOMINAL_PAIN"}]}\n```\n请查收。'
    result = OpenAICompatibleProvider._extract_json_object(content)
    assert result["extractedTags"][0]["code"] == "ABDOMINAL_PAIN"


def test_openai_compatible_extracts_first_valid_nested_json_among_multiple_blocks():
    content = '说明 {不是 JSON}；正式结果：{"selectedTagCodes":["ABDOMINAL_PAIN"],"tagEvidence":{"ABDOMINAL_PAIN":"肚子不舒服"}}；附加：{"ignored":true}'
    result = OpenAICompatibleProvider._extract_json_object(content)
    assert result["selectedTagCodes"] == ["ABDOMINAL_PAIN"]
    assert result["tagEvidence"]["ABDOMINAL_PAIN"] == "肚子不舒服"


def test_openai_compatible_rejects_content_without_json():
    with pytest.raises(RuntimeError, match="AI_INVALID_JSON_OBJECT"):
        OpenAICompatibleProvider._extract_json_object("好的，但这次没有提供结构化内容。")


def test_openai_compatible_maps_http_and_response_shape_errors(monkeypatch):
    monkeypatch.setattr(providers, "settings", SimpleNamespace(
        base_url="https://provider.example/v1", api_key="test-only",
        model="model-test", request_timeout_seconds=9))

    def unavailable(*args, **kwargs):
        request_value = httpx.Request("POST", "https://provider.example/v1/chat/completions")
        return httpx.Response(503, request=request_value)

    monkeypatch.setattr(providers.httpx, "post", unavailable)
    with pytest.raises(RuntimeError, match="AI_PROVIDER_HTTP_ERROR"):
        OpenAICompatibleProvider().generate(request(), [])

    class InvalidResponse:
        def raise_for_status(self): return None
        def json(self): return {"unexpected": []}

    monkeypatch.setattr(providers.httpx, "post", lambda *args, **kwargs: InvalidResponse())
    with pytest.raises(RuntimeError, match="AI_INVALID_PROVIDER_RESPONSE"):
        OpenAICompatibleProvider().generate(request(), [])


def test_complaint_provider_maps_selected_codes_to_server_catalog(monkeypatch):
    monkeypatch.setattr(providers, "settings", SimpleNamespace(
        base_url="https://provider.example/v1", api_key="test-only",
        model="model-test", max_output_tokens=1800))
    adapter = OpenAICompatibleProvider()
    monkeypatch.setattr(adapter, "_call", lambda payload: {
        "selectedTagCodes": ["ABDOMINAL_PAIN", "INVENTED_TAG", "ABDOMINAL_PAIN"],
        "tagEvidence": {"ABDOMINAL_PAIN": "肚子痛"},
        "normalizedSummary": "患者自述腹部疼痛。",
        "structuredFacts": {"duration": "", "onset": "", "location": "腹部", "character": "",
                            "aggravatingFactors": [], "relievingFactors": [],
                            "associatedSymptoms": [], "activityImpact": ""},
        "riskSignals": [], "missingQuestions": [], "uncertainties": [],
    })
    request = ComplaintStructureRequest.model_validate({
        "rawComplaint": "我肚子痛",
        "selectedTags": [],
        "availableTags": [
            {"code": "HEADACHE", "displayName": "头痛", "category": "头部与神经"},
            {"code": "ABDOMINAL_PAIN", "displayName": "腹痛", "category": "消化系统"},
        ],
    })
    result = adapter.structure_complaint(request)
    assert result["extractedTags"] == [{
        "code": "ABDOMINAL_PAIN", "displayName": "腹痛", "category": "消化系统",
        "source": "ai_extracted", "confidence": None, "evidenceText": "肚子痛",
        "confirmationStatus": "proposed",
    }]
