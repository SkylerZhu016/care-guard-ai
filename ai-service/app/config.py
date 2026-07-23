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
    knowledge_base_url: str = os.getenv("KNOWLEDGE_BASE_URL", "")
    prompt_version: str = "triage-v2-multi-agent"
    knowledge_base_version: str = "pgvector-kb-v3"
    rule_set_version: str = "red-flags-v1"
    max_output_tokens: int = int(os.getenv("AI_MAX_OUTPUT_TOKENS", "1800"))
    request_timeout_seconds: float = float(os.getenv("AI_REQUEST_TIMEOUT_SECONDS", "20"))
    rag_top_k: int = int(os.getenv("RAG_TOP_K", "6"))


settings = Settings()

