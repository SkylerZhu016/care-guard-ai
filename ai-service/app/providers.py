import hashlib
import json
from typing import Dict, List
import httpx

from .config import settings
from .schemas import AnalysisRequest, Urgency


URGENCY_RANK = {Urgency.ROUTINE: 0, Urgency.URGENT: 1, Urgency.EMERGENCY: 2}


class ProviderTimeoutError(RuntimeError):
    pass


class DeterministicFakeProvider:
    name = "fake"

    def generate(self, request: AnalysisRequest, evidence: List[Dict[str, str]]) -> Dict:
        canonical = json.dumps(request.model_dump(mode="json"), ensure_ascii=False, sort_keys=True)
        proposed = request.ruleUrgency
        codes = {symptom.code for symptom in request.symptoms}
        if {"CHEST_PAIN", "DYSPNEA"}.issubset(codes) or "ALTERED_CONSCIOUSNESS" in codes:
            proposed = Urgency.EMERGENCY
        elif any(symptom.severity >= 7 for symptom in request.symptoms):
            proposed = max((proposed, Urgency.URGENT), key=lambda value: URGENCY_RANK[value])
        summary_codes = "、".join(symptom.code for symptom in request.symptoms)
        return {
            "caseSummary": f"成人合成教学病例；主诉：{request.chiefComplaint[:120]}；结构化症状：{summary_codes}。",
            "proposedUrgency": proposed,
            "rationale": ["规则结果是不可降低的安全底线。", "建议结合所列公开教学材料由医务人员复核。"],
            "missingQuestions": ["症状是否持续或加重？", "是否出现新的红旗信号？"],
            "evidence": evidence,
            "seedHash": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
        }

    def critique(self, request: AnalysisRequest, draft: Dict, evidence: List[Dict[str, str]]) -> Dict:
        return {"violations": [], "review": "规则单调性、引用归属和越界输出已由独立安全角色复核。"}


class OpenAICompatibleProvider:
    name = "openai-compatible"

    def generate(self, request: AnalysisRequest, evidence: List[Dict[str, str]]) -> Dict:
        if not settings.base_url or not settings.api_key:
            raise RuntimeError("AI_PROVIDER_CONFIG_MISSING")
        payload = {
            "model": settings.model,
            "temperature": 0,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": "只整理教学模拟病例，不诊断，不开药；输出严格 JSON。"},
                {"role": "user", "content": json.dumps({"case": request.model_dump(mode="json"), "evidence": evidence}, ensure_ascii=False)},
            ],
        }
        return self._call(payload)

    def critique(self, request: AnalysisRequest, draft: Dict, evidence: List[Dict[str, str]]) -> Dict:
        payload = {
            "model": settings.model,
            "temperature": 0,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": "你是独立安全审查角色。只检查草案是否越过教学信息整理边界、降低规则紧急度或使用无来源证据。仅输出 JSON：{\"violations\":[],\"review\":\"\"}。"},
                {"role": "user", "content": json.dumps({"case": request.model_dump(mode="json"), "draft": draft, "evidence": evidence}, ensure_ascii=False)},
            ],
        }
        return self._call(payload)

    def _call(self, payload: Dict) -> Dict:
        try:
            response = httpx.post(f"{settings.base_url.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {settings.api_key}"}, json=payload, timeout=20)
            response.raise_for_status()
            content=response.json()["choices"][0]["message"]["content"]
            result=json.loads(content)
            if not isinstance(result,dict): raise RuntimeError("AI_INVALID_JSON_OBJECT")
            return result
        except httpx.TimeoutException as exc:
            raise ProviderTimeoutError("AI_TIMEOUT") from exc


def get_provider():
    return DeterministicFakeProvider() if settings.provider == "fake" else OpenAICompatibleProvider()

