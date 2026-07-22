"""嵌入器 —— Hash(256) 本地降级 + 可插拔 OpenAI 兼容"""
import hashlib
import math
import re
import logging
from typing import Optional
import httpx
from app.core.config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


def tokenize(text: str) -> list[str]:
    """简易分词：中文按字 bigram + 英文按词"""
    tokens = []
    # 英文词
    for w in re.findall(r"[a-zA-Z]{2,}", text.lower()):
        tokens.append(w)
    # 中文 bigram
    cn = re.findall(r"[\u4e00-\u9fff]", text)
    for i in range(len(cn)):
        tokens.append(cn[i])
        if i + 1 < len(cn):
            tokens.append(cn[i] + cn[i + 1])
    return tokens


class HashEmbedder:
    """特征哈希嵌入：确定、离线、无依赖"""

    def __init__(self, dim: int = None):
        self.dim = dim or settings.VECTOR_DIM

    def embed(self, text: str) -> list[float]:
        tokens = tokenize(text)
        if not tokens:
            return [0.0] * self.dim
        vec = [0.0] * self.dim
        for tok in tokens:
            h = int(hashlib.md5(tok.encode()).hexdigest(), 16)
            idx = h % self.dim
            sign = 1.0 if (h >> 8) % 2 == 0 else -1.0
            vec[idx] += sign
        # L2 归一
        norm = math.sqrt(sum(v * v for v in vec)) or 1.0
        return [v / norm for v in vec]


class OpenAIEmbedder:
    """OpenAI 兼容嵌入（可选）"""

    def __init__(self):
        self.api_base = settings.EMBEDDING_API_BASE
        self.api_key = settings.EMBEDDING_API_KEY
        self.model = settings.EMBEDDING_MODEL
        self.dim = settings.EMBEDDING_DIM

    def embed(self, text: str) -> list[float]:
        headers = {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}
        payload = {"model": self.model, "input": text[:8000]}
        with httpx.Client(timeout=30) as client:
            resp = client.post(f"{self.api_base}/embeddings", headers=headers, json=payload)
            resp.raise_for_status()
            return resp.json()["data"][0]["embedding"][: self.dim]


def get_embedder():
    if settings.EMBEDDING_API_KEY and settings.EMBEDDING_API_BASE:
        return OpenAIEmbedder()
    return HashEmbedder()
