import hashlib
import json
from typing import Dict, List
import httpx

from .config import settings
from .schemas import AnalysisRequest


URGENCY_RANK = {"ROUTINE": 0, "URGENT": 1, "EMERGENCY": 2}


class ProviderTimeoutError(RuntimeError):
    pass


class DeterministicFakeProvider:
    name = "fake"

    def generate(self, request: AnalysisRequest, evidence: List[Dict[str, str]]) -> Dict:
        canonical = json.dumps(request.model_dump(mode="json"), ensure_ascii=False, sort_keys=True)
        proposed = request.ruleUrgency
        summary = "；".join(
            f"{symptom.name or symptom.code}（开始：{symptom.onsetRange}，状态：{symptom.currentStatus}，活动影响：{symptom.activityImpact}）"
            for symptom in request.symptoms
        )
        return {
            "caseSummary": f"患者主诉：{request.chiefComplaint[:120]}；已记录情况：{summary}。",
            "proposedUrgency": proposed,
            "rationale": ["自动处理仅整理患者填写的事实和规则结果。", "未覆盖内容及自动结果均交由医务人员复核。"],
            "missingQuestions": ["仍为不知道或说不清的项目可在人工审核时继续核对。"],
            "evidence": evidence,
            "seedHash": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
        }

    def critique(self, request: AnalysisRequest, draft: Dict, evidence: List[Dict[str, str]]) -> Dict:
        return {"violations": [], "review": "规则边界、引用归属和越界输出已由独立安全角色复核。"}


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
                {"role": "system", "content": "只整理患者填写的事实、遗漏问题和证据，不诊断、不开药、不自行改变规则结果；输出严格 JSON。"},
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
                {"role": "system", "content": "你是独立安全审查角色。只检查草案是否越过信息整理边界、改变规则紧急度或使用无来源证据。仅输出 JSON：{\"violations\":[],\"review\":\"\"}。"},
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

