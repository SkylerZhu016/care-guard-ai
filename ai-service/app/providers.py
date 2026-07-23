import hashlib
import json
from typing import Dict, List
import httpx

from .config import settings
from .schemas import AnalysisRequest, ComplaintStructureRequest


URGENCY_RANK = {"ROUTINE": 0, "URGENT": 1, "EMERGENCY": 2}


class ProviderTimeoutError(RuntimeError):
    pass


class DeterministicFakeProvider:
    name = "fake"

    def generate(self, request: AnalysisRequest, evidence: List[Dict[str, str]]) -> Dict:
        canonical = json.dumps(request.model_dump(mode="json"), ensure_ascii=False, sort_keys=True)
        proposed = request.ruleUrgency
        labels = {
            "JUST_NOW": "刚刚", "TODAY": "今天", "ONE_TO_THREE_DAYS": "1–3 天", "MORE_THAN_THREE_DAYS": "3 天以上",
            "CONTINUOUS": "持续", "INTERMITTENT": "间歇", "RELIEVED": "已经缓解",
            "PRESENT": "仍存在", "NOT_PRESENT": "目前没有", "NO_IMPACT": "不影响",
            "NEEDS_REST": "需要停下休息", "UNABLE_NORMAL_ACTIVITY": "无法正常活动", "UNKNOWN": "不知道/说不清",
        }
        summary = "；".join(
            f"{symptom.name or symptom.code}（开始：{labels.get(symptom.onsetRange, symptom.onsetRange)}，"
            f"状态：{labels.get(symptom.currentStatus, symptom.currentStatus)}，"
            f"活动影响：{labels.get(symptom.activityImpact, symptom.activityImpact)}）"
            for symptom in request.symptoms
        )
        return {
            "caseSummary": f"患者主诉：{request.chiefComplaint[:120]}；已记录情况：{summary}。",
            "proposedUrgency": proposed,
            "rationale": ["自动处理仅整理患者填写的事实和规则结果。", "未覆盖内容及自动结果均交由医务人员复核。"],
            "missingQuestions": ["仍为不知道或说不清的项目可在人工审核时继续核对。"],
            "structuredSummary": f"患者自述{request.chiefComplaint[:180]}。已按症状、时间、当前状态和活动影响完成结构化整理。",
            "keyFindings": [f"记录到{symptom.name or symptom.code}：当前状态为{labels.get(symptom.currentStatus, symptom.currentStatus)}" for symptom in request.symptoms],
            "abnormalSignals": list(request.ruleReasonCodes),
            "areasToRuleOut": ["结合患者原话、健康资料与检查结果进一步核对相关系统风险"],
            "recommendedAdditionalInformation": ["补充不知道或说不清的时间、诱因、伴随表现和既往信息"],
            "riskSignals": list(request.ruleReasonCodes),
            "evidenceSynthesis": f"已检索到 {len(evidence)} 条可追溯资料；引用仅作为医务人员复核参考。",
            "uncertainties": ["自动整理不能确认病因，未提供的信息不作推断"],
            "clinicalThinkingPrompts": ["核对当前是否仍存在危险信号", "判断是否需要补充检查或线下评估"],
            "evidence": evidence,
            "seedHash": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
        }

    def critique(self, request: AnalysisRequest, draft: Dict, evidence: List[Dict[str, str]]) -> Dict:
        return {"violations": [], "review": "规则边界、引用归属和越界输出已由独立安全角色复核。"}

    def structure_complaint(self, request: ComplaintStructureRequest) -> Dict:
        text = request.rawComplaint
        mappings = [
            ("CHEST_PAIN", "胸痛", "胸部与呼吸", ("胸痛", "胸口痛", "胸口疼")),
            ("CHEST_TIGHTNESS", "胸闷", "胸部与呼吸", ("胸闷", "胸口闷", "发闷", "闷")),
            ("DYSPNEA", "呼吸困难", "胸部与呼吸", ("喘不上气", "气不够", "呼吸困难", "气短")),
            ("HEADACHE", "头痛", "头部与神经", ("头痛", "头疼")),
            ("DIZZINESS", "头晕", "头部与神经", ("头晕", "眩晕")),
            ("ABDOMINAL_PAIN", "腹痛", "消化系统", ("腹痛", "肚子痛", "肚子疼")),
            ("NAUSEA", "恶心", "消化系统", ("恶心", "想吐")),
            ("VOMITING", "呕吐", "消化系统", ("呕吐", "吐了")),
            ("COUGH", "咳嗽", "胸部与呼吸", ("咳嗽", "咳")),
            ("FEVER", "发热", "全身不适", ("发热", "发烧")),
            ("FATIGUE", "乏力", "全身不适", ("乏力", "没力气")),
            ("PALPITATIONS", "心悸", "胸部与呼吸", ("心悸", "心慌")),
        ]
        selected = {tag.code for tag in request.selectedTags}
        tags = []
        for code, name, category, aliases in mappings:
            evidence = next((alias for alias in aliases if alias in text), None)
            if evidence and code not in selected:
                tags.append({"code": code, "displayName": name, "category": category, "source": "ai_extracted",
                             "confidence": 0.9, "evidenceText": evidence, "confirmationStatus": "proposed"})
        aggravating = ["活动后加重"] if any(key in text for key in ("走快", "活动后", "运动后", "上楼")) else []
        duration = next((value for value in ("这两天", "今天", "昨天", "一周", "几天") if value in text), "")
        character = "间歇出现" if any(key in text for key in ("有时候", "一阵一阵", "间歇")) else ""
        risk = []
        if any(tag["code"] == "CHEST_PAIN" for tag in tags) and any(tag["code"] == "DYSPNEA" for tag in tags):
            risk.append("胸痛与呼吸困难同时出现，需优先人工核对当前状态")
        return {"normalizedSummary": text[:260].rstrip("。") + "。", "extractedTags": tags,
                "structuredFacts": {"duration": duration, "onset": "", "location": "胸部" if "胸" in text else "",
                    "character": character, "aggravatingFactors": aggravating, "relievingFactors": [],
                    "associatedSymptoms": [tag["displayName"] for tag in tags], "activityImpact": "活动时更明显" if aggravating else ""},
                "riskSignals": risk, "missingQuestions": ["症状目前是否仍存在？", "是否还有其他伴随不适？"],
                "uncertainties": ["患者口语可能存在歧义，提取结果需由患者确认"]}


class OpenAICompatibleProvider:
    name = "openai-compatible"

    def generate(self, request: AnalysisRequest, evidence: List[Dict[str, str]]) -> Dict:
        if not settings.base_url or not settings.api_key:
            raise RuntimeError("AI_PROVIDER_CONFIG_MISSING")
        payload = {
            "model": settings.model,
            "temperature": 0,
            "max_tokens": getattr(settings, "max_output_tokens", 1800),
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": "只整理患者填写的事实、遗漏问题和证据，不诊断、不开药、不自行改变规则结果；输出严格 JSON。"},
                {"role": "system", "content": "严格 JSON 必须包含 caseSummary、proposedUrgency、rationale、missingQuestions、structuredSummary、keyFindings、abnormalSignals、areasToRuleOut、recommendedAdditionalInformation、riskSignals、evidenceSynthesis、uncertainties、clinicalThinkingPrompts、evidence；所有列表均为字符串数组；proposedUrgency 只能为 null、ROUTINE、URGENT 或 EMERGENCY，规则等级为空时必须为 null。"},
                {"role": "user", "content": json.dumps({"case": request.model_dump(mode="json"), "evidence": evidence}, ensure_ascii=False)},
            ],
        }
        result = self._call(payload)
        # Urgency is owned by the deterministic Java rule engine. The model is
        # never allowed to create or lower a level, even if it ignores prompt text.
        result["proposedUrgency"] = request.ruleUrgency.value if request.ruleUrgency else None
        return result

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

    def structure_complaint(self, request: ComplaintStructureRequest) -> Dict:
        if not settings.base_url or not settings.api_key:
            raise RuntimeError("AI_PROVIDER_CONFIG_MISSING")
        payload = {"model": settings.model, "temperature": 0, "max_tokens": min(getattr(settings, "max_output_tokens", 1800), 1200),
            "response_format": {"type": "json_object"}, "messages": [
                {"role": "system", "content": "你只把患者口语主诉整理为医学语义结构，不诊断、不处方、不猜测未提供信息。保留手选标签且不要重复输出。严格输出 JSON：normalizedSummary；extractedTags（code/displayName/category/source=ai_extracted/confidence/evidenceText/confirmationStatus=proposed）；structuredFacts（duration/onset/location/character/aggravatingFactors/relievingFactors/associatedSymptoms/activityImpact）；riskSignals；missingQuestions；uncertainties。"},
                {"role": "user", "content": json.dumps(request.model_dump(mode="json"), ensure_ascii=False)},
            ]}
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

