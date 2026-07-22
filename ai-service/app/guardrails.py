import re
from typing import List


ATTACK_PATTERNS = [
    r"忽略.{0,12}(规则|指令)",
    r"(system prompt|系统提示词)",
    r"(直接诊断|替我诊断|开药|处方|剂量)",
    r"(扮演|假装).{0,8}(医生|管理员)",
    r"(override|bypass).{0,12}(rule|safety)",
]
PII_PATTERNS = [r"\b\d{17}[0-9Xx]\b", r"\b1[3-9]\d{9}\b"]


def inspect_input(chief_complaint: str, free_text: str) -> List[str]:
    text = f"{chief_complaint}\n{free_text}"
    reasons = []
    if any(re.search(pattern, text, re.IGNORECASE) for pattern in ATTACK_PATTERNS):
        reasons.append("PROMPT_INJECTION_OR_SCOPE_VIOLATION")
    if any(re.search(pattern, text) for pattern in PII_PATTERNS):
        reasons.append("POTENTIAL_REAL_IDENTIFIER")
    return reasons


def validate_citations(chunk_ids: List[str], valid_ids: set) -> bool:
    return bool(chunk_ids) and all(chunk_id in valid_ids for chunk_id in chunk_ids)

