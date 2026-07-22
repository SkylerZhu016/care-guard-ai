import json
from types import SimpleNamespace

import httpx
import pytest

import app.providers as providers
from app.providers import OpenAICompatibleProvider, ProviderTimeoutError
from app.schemas import AnalysisRequest


def request():
    return AnalysisRequest.model_validate({
        "runId": "run-adapter-001", "visitId": "visit-adapter-001", "ageBand": "ADULT",
        "chiefComplaint": "合成轻度疲劳", "symptoms": [{"code": "FATIGUE", "severity": 2}],
        "freeText": "仅为教学模拟", "ruleUrgency": "ROUTINE", "ruleReasonCodes": ["NO_CONFIGURED_RED_FLAG"],
    })


def test_openai_compatible_uses_separate_generation_and_critic_calls(monkeypatch):
    monkeypatch.setattr(providers, "settings", SimpleNamespace(base_url="https://provider.example/v1", api_key="test-only", model="model-test"))
    calls = []
    responses = [
        {"caseSummary": "合成病例摘要", "proposedUrgency": "ROUTINE", "rationale": ["规则优先"], "missingQuestions": [], "evidence": []},
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
    assert "只整理教学模拟病例" in calls[0]["payload"]["messages"][0]["content"]
    assert "独立安全审查角色" in calls[1]["payload"]["messages"][0]["content"]
    assert critic["violations"] == []


def test_openai_compatible_maps_timeout(monkeypatch):
    monkeypatch.setattr(providers, "settings", SimpleNamespace(base_url="https://provider.example/v1", api_key="test-only", model="model-test"))

    def timeout(*args, **kwargs):
        raise httpx.TimeoutException("test timeout")

    monkeypatch.setattr(providers.httpx, "post", timeout)
    with pytest.raises(ProviderTimeoutError, match="AI_TIMEOUT"):
        OpenAICompatibleProvider().generate(request(), [])
