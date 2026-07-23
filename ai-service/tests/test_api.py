from fastapi.testclient import TestClient
from app.main import app
from app.config import settings


client = TestClient(app)


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
        json={"rawComplaint": "这两天胸口有点闷，有时候会痛，走快了更明显。",
              "selectedTags": [{"code": "CHEST_PAIN", "displayName": "胸痛", "category": "胸部与呼吸",
                                "source": "user_selected", "confirmationStatus": "confirmed"}],
              "ageBand": "ADULT"})
    assert response.status_code == 200
    payload = response.json()
    assert payload["normalizedSummary"]
    assert any(tag["code"] == "CHEST_TIGHTNESS" for tag in payload["extractedTags"])
    assert not any(tag["code"] == "CHEST_PAIN" for tag in payload["extractedTags"])
    assert payload["structuredFacts"]["aggravatingFactors"] == ["活动后加重"]
    assert payload["disclaimer"].startswith("AI 仅用于整理")

