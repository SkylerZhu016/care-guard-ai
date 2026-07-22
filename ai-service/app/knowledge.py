from typing import Dict, List

import httpx

from .config import settings


# Isolated unit tests do not start Spring/PostgreSQL. Production Compose always
# sets KNOWLEDGE_BASE_URL and therefore uses the pgvector-backed service.
TEST_FALLBACK: List[Dict[str, str]] = [
    {"guidelineId": "project-red-flags", "versionId": "red-flags-v2", "chunkId": "chunk-red-flag-chest-pain-001",
     "title": "成人急症重点表现测试夹具", "section": "胸痛伴随危险信号", "sourceUrl": "https://example.org/medsim/internal-fixture",
     "licenseNote": "测试回退夹具", "quote": "胸痛伴呼吸困难、晕厥或意识异常需要立即进入人工急症评估。"},
    {"guidelineId": "project-red-flags", "versionId": "red-flags-v2", "chunkId": "chunk-red-flag-consciousness-001",
     "title": "成人急症重点表现测试夹具", "section": "意识状态", "sourceUrl": "https://example.org/medsim/internal-fixture",
     "licenseNote": "测试回退夹具", "quote": "新发意识异常或晕厥应作为高优先级信号进入人工评估，不应由自动系统给出低风险结论。"},
    {"guidelineId": "who-hearts-2020", "versionId": "who-hearts-fixture-v1", "chunkId": "chunk-manual-review-001",
     "title": "HEARTS Technical Package", "section": "基层高血压工程化随访摘要", "sourceUrl": "https://www.who.int/publications/i/item/9789240001367",
     "licenseNote": "公开访问；再利用以 WHO 发布页为准", "quote": "未命中已配置急症规则时仍应由医务人员结合结构化信息复核；自动结果不能替代人工判断。"},
]


def search_guidelines(symptom_codes: List[str], query: str, limit: int = 4) -> List[Dict[str, str]]:
    if settings.knowledge_base_url:
        response = httpx.post(
            settings.knowledge_base_url,
            headers={"X-Internal-Token": settings.internal_token},
            json={"symptomCodes": symptom_codes, "query": query, "limit": limit},
            timeout=8,
        )
        response.raise_for_status()
        results = response.json()
        if not results:
            raise RuntimeError("KNOWLEDGE_RETRIEVAL_EMPTY")
        return results
    codes = set(symptom_codes)
    if "ALTERED_CONSCIOUSNESS" in codes or "SYNCOPE" in codes:
        return [TEST_FALLBACK[1], TEST_FALLBACK[0]][:limit]
    if "CHEST_PAIN" in codes or "DYSPNEA" in codes:
        return [TEST_FALLBACK[0], TEST_FALLBACK[1]][:limit]
    return [TEST_FALLBACK[2]][:limit]
