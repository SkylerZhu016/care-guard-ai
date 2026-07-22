from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    app_env: str = os.getenv("APP_ENV", "development")
    provider: str = os.getenv("AI_PROVIDER", "fake")
    model: str = os.getenv("AI_MODEL", "fake-v1")
    base_url: str = os.getenv("AI_BASE_URL", "")
    api_key: str = os.getenv("AI_API_KEY", "")
    redis_url: str = os.getenv("REDIS_URL", "redis://localhost:6379/0")
    internal_token: str = os.getenv("INTERNAL_SERVICE_TOKEN", "change-me-local-only")
    prompt_version: str = "triage-v1"
    knowledge_base_version: str = "demo-kb-v1"
    rule_set_version: str = "red-flags-v1"


settings = Settings()

