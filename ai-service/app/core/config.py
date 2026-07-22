"""应用配置 —— 全部从环境变量读取"""
import os
from functools import lru_cache


class Settings:
    # 数据库
    DATABASE_URL: str = os.getenv("DATABASE_URL", "postgresql+psycopg2://med:med_dev_123@localhost:5432/medplatform")

    # 内部令牌（与 Spring Boot 共享）
    INTERNAL_TOKEN: str = os.getenv("INTERNAL_TOKEN", "internal_dev_token")

    # 后端内部接口
    BACKEND_URL: str = os.getenv("BACKEND_URL", "http://localhost:8080")

    # LLM
    MOCK_LLM: bool = os.getenv("MOCK_LLM", "true").lower() == "true"
    LLM_API_BASE: str = os.getenv("LLM_API_BASE", "")
    LLM_API_KEY: str = os.getenv("LLM_API_KEY", "")
    LLM_MODEL: str = os.getenv("LLM_MODEL", "gpt-4o-mini")
    LLM_TIMEOUT: int = int(os.getenv("LLM_TIMEOUT", "30"))
    LLM_MAX_RETRIES: int = int(os.getenv("LLM_MAX_RETRIES", "2"))
    LLM_MAX_INPUT_CHARS: int = int(os.getenv("LLM_MAX_INPUT_CHARS", "12000"))
    LLM_MAX_TOKENS: int = int(os.getenv("LLM_MAX_TOKENS", "2048"))

    # Embedding
    EMBEDDING_API_BASE: str = os.getenv("EMBEDDING_API_BASE", "")
    EMBEDDING_API_KEY: str = os.getenv("EMBEDDING_API_KEY", "")
    EMBEDDING_MODEL: str = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")
    EMBEDDING_DIM: int = int(os.getenv("EMBEDDING_DIM", "256"))

    # RAG
    RAG_TOP_K: int = int(os.getenv("RAG_TOP_K", "5"))
    RAG_MIN_SCORE: float = float(os.getenv("RAG_MIN_SCORE", "0.15"))

    # 向量
    VECTOR_DIM: int = 256  # 与 V1__init.sql vector(256) 一致


@lru_cache
def get_settings() -> Settings:
    return Settings()
