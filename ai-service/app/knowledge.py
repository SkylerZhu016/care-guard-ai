from typing import Dict, List


GUIDELINES: List[Dict[str, str]] = [
    {
        "guidelineId": "guideline-public-demo-001",
        "chunkId": "chunk-red-flag-chest-pain-001",
        "title": "成人急症红旗识别教学摘录",
        "section": "胸痛伴随危险信号",
        "topics": "CHEST_PAIN DYSPNEA SYNCOPE",
        "quote": "教学模拟中，胸痛伴呼吸困难、晕厥或意识异常属于需要立即人工急症评估的红旗组合。",
    },
    {
        "guidelineId": "guideline-public-demo-001",
        "chunkId": "chunk-red-flag-consciousness-001",
        "title": "成人急症红旗识别教学摘录",
        "section": "意识状态",
        "topics": "ALTERED_CONSCIOUSNESS SYNCOPE",
        "quote": "新发意识异常或晕厥应作为高优先级红旗信号进入人工评估，不应由自动系统给出低风险结论。",
    },
    {
        "guidelineId": "guideline-public-demo-002",
        "chunkId": "chunk-routine-followup-001",
        "title": "高血压教学随访流程说明",
        "section": "工程化随访记录",
        "topics": "HEADACHE FATIGUE ROUTINE",
        "quote": "无急症红旗的教学病例可由医务人员结合结构化信息安排常规复核；自动结果不能替代人工判断。",
    },
]


VALID_CHUNK_IDS = {item["chunkId"] for item in GUIDELINES}


def search_guidelines(symptom_codes: List[str], limit: int = 4) -> List[Dict[str, str]]:
    scored = []
    for item in GUIDELINES:
        score = sum(1 for code in symptom_codes if code in item["topics"])
        scored.append((score, item))
    ranked = [item for score, item in sorted(scored, key=lambda row: row[0], reverse=True) if score > 0]
    return (ranked or [GUIDELINES[-1]])[:limit]

