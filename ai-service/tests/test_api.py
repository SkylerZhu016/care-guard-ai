from fastapi.testclient import TestClient
from app.main import app
from app.config import settings


client = TestClient(app)
available_tags = [
    {"code": "CHEST_PAIN", "displayName": "胸痛", "category": "胸部与呼吸"},
    {"code": "COUGH", "displayName": "咳嗽", "category": "胸部与呼吸"},
    {"code": "ABDOMINAL_PAIN", "displayName": "腹痛", "category": "消化系统"},
    {"code": "ALTERED_CONSCIOUSNESS", "displayName": "意识异常", "category": "头部与神经"},
    {"code": "LIMB_WEAKNESS_NUMBNESS", "displayName": "肢体无力或麻木", "category": "头部与神经"},
    {"code": "SLEEP_PROBLEM", "displayName": "睡眠问题", "category": "睡眠与情绪"},
]


def test_live_health_exposes_fake_provider():
    response = client.get("/health/live")
    assert response.status_code == 200
    assert response.json()["provider"] == "fake"


def test_internal_route_requires_service_token():
    response = client.get("/internal/v1/jobs/missing")
    assert response.status_code == 401


def test_complaint_structure_is_strict_and_keeps_selected_tags_separate():
    response = client.post("/internal/v1/complaint-structure",
        headers={"X-Internal-Token": settings.internal_token},
        json={"rawComplaint": "这两天胸口有点痛，还一直咳嗽，走快了更明显。",
              "selectedTags": [{"code": "CHEST_PAIN", "displayName": "胸痛", "category": "胸部与呼吸",
                                "source": "user_selected", "confirmationStatus": "confirmed"}],
              "availableTags": available_tags,
              "ageBand": "ADULT"})
    assert response.status_code == 200
    payload = response.json()
    assert payload["normalizedSummary"]
    assert any(tag["code"] == "COUGH" for tag in payload["extractedTags"])
    assert not any(tag["code"] == "CHEST_PAIN" for tag in payload["extractedTags"])
    assert payload["structuredFacts"]["aggravatingFactors"] == ["活动后加重"]
    assert payload["disclaimer"].startswith("AI 仅用于整理")


def test_everyday_abdominal_pain_is_mapped_to_catalog_tag():
    response = client.post("/internal/v1/complaint-structure",
        headers={"X-Internal-Token": settings.internal_token},
        json={"rawComplaint": "我肚子痛", "selectedTags": [], "availableTags": available_tags, "ageBand": "ADULT"})
    assert response.status_code == 200
    assert [tag["code"] for tag in response.json()["extractedTags"]] == ["ABDOMINAL_PAIN"]


def test_everyday_discomfort_and_numbness_are_mapped_to_catalog_tags():
    cases = [
        ("我肚子有点不舒服", "ABDOMINAL_PAIN"),
        ("我的脚发麻", "LIMB_WEAKNESS_NUMBNESS"),
    ]
    for complaint, expected_code in cases:
        response = client.post("/internal/v1/complaint-structure",
            headers={"X-Internal-Token": settings.internal_token},
            json={"rawComplaint": complaint, "selectedTags": [],
                  "availableTags": available_tags, "ageBand": "ADULT"})
        assert response.status_code == 200
        assert [tag["code"] for tag in response.json()["extractedTags"]] == [expected_code]


def test_catalog_whitelist_drops_model_invented_tag(monkeypatch):
    class RogueProvider:
        name = "test"

        def structure_complaint(self, request):
            return {
                "normalizedSummary": request.rawComplaint,
                "extractedTags": [{"code": "INVENTED_DISEASE", "displayName": "虚构标签",
                                   "category": "其他", "source": "ai_extracted",
                                   "confirmationStatus": "proposed"}],
                "structuredFacts": {},
                "riskSignals": [],
                "missingQuestions": [],
                "uncertainties": [],
            }

    monkeypatch.setattr("app.providers.get_provider", lambda: RogueProvider())
    response = client.post("/internal/v1/complaint-structure",
        headers={"X-Internal-Token": settings.internal_token},
        json={"rawComplaint": "我肚子痛", "selectedTags": [], "availableTags": available_tags, "ageBand": "ADULT"})
    assert response.status_code == 200
    assert response.json()["extractedTags"] == []

