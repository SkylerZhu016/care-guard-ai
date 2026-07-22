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

