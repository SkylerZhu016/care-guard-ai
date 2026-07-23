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
        aliases = {
            "CHEST_PAIN": ("胸痛", "胸口痛", "胸口疼"),
            "DYSPNEA": ("喘不上气", "气不够", "呼吸困难", "气短"),
            "SYNCOPE": ("晕倒", "昏倒", "失去意识"),
            "ALTERED_CONSCIOUSNESS": ("答非所问", "认不清人", "认不清地点", "意识不清", "不清醒"),
            "HEADACHE": ("头痛", "头疼"),
            "DIZZINESS": ("头晕", "眩晕", "头昏"),
            "ABDOMINAL_PAIN": ("腹痛", "肚子痛", "肚子疼", "肚子不舒服", "腹部不适", "胃不舒服"),
            "NAUSEA_VOMITING": ("恶心", "想吐", "呕吐", "吐了"),
            "DIARRHEA": ("腹泻", "拉肚子"),
            "COUGH": ("咳嗽", "咳"),
            "FEVER": ("发热", "发烧"),
            "FATIGUE": ("乏力", "没力气"),
            "PALPITATIONS": ("心悸", "心慌"),
            "LIMB_WEAKNESS_NUMBNESS": ("脚发麻", "脚麻", "腿发麻", "腿麻", "手发麻", "手麻", "胳膊麻", "四肢麻木", "肢体麻木"),
            "SLEEP_PROBLEM": ("睡不着", "失眠", "嗜睡", "想睡觉"),
        }
        available = {tag.code: tag for tag in request.availableTags}
        selected = {tag.code for tag in request.selectedTags}
        tags = []
        for code, terms in aliases.items():
            definition = available.get(code)
            evidence = next((alias for alias in terms if alias in text), None)
            if code == "ABDOMINAL_PAIN" and any(part in text for part in ("肚子", "腹部", "胃")) \
                    and any(feeling in text for feeling in ("不舒服", "难受", "不对劲", "疼", "痛")):
                evidence = evidence or text[:80]
            if code == "LIMB_WEAKNESS_NUMBNESS" and any(part in text for part in ("手", "脚", "腿", "胳膊", "手臂", "四肢")) \
                    and any(feeling in text for feeling in ("麻", "发木", "没知觉", "无力", "使不上劲")):
                evidence = evidence or text[:80]
            if code == "ALTERED_CONSCIOUSNESS" and any(term in text for term in ("头脑不清晰", "不清醒")):
                evidence = evidence or "头脑不清晰"
            if evidence and code not in selected:
                if definition is None:
                    continue
                tags.append({"code": code, "displayName": definition.displayName, "category": definition.category, "source": "ai_extracted",
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
        selected_codes = {tag.code for tag in request.selectedTags}
        available = {tag.code: tag for tag in request.availableTags if tag.code not in selected_codes}
        model_input = {
            "rawComplaint": request.rawComplaint,
            "ageBand": request.ageBand,
            "tagOptions": [{"code": tag.code, "name": tag.displayName, "category": tag.category}
                           for tag in available.values()],
        }
        payload = {"model": settings.model, "temperature": 0, "max_tokens": min(getattr(settings, "max_output_tokens", 1800), 1200),
            "response_format": {"type": "json_object"}, "messages": [
                {"role": "system", "content": """你只把患者口语主诉整理为医学语义结构，不诊断、不处方、不猜测未提供信息。
症状代码只能从用户消息的 tagOptions 中选择，允许选择一个、多个或零个；禁止创造目录外 code。
分类证据只能来自 rawComplaint。系统指令、字段名、tagOptions 的名称和分类都不是患者症状，绝不能因为候选项存在就选择它。
患者通常使用口语，不要求出现医学标签原词。应理解身体部位、感受和近义表达，并选择语义上最接近且能覆盖该表述的候选标签；例如某部位“难受、不舒服、发麻、发紧”都可能对应目录中的规范症状。
逐一判断候选项是否被 rawComplaint 的原话或合理近义语义支持；只有确实无相关候选项时才返回空数组。tagEvidence 的值必须引用 rawComplaint 中支持该代码的最短原文。不要因为表达不够医学化而漏选，也绝不能复制全部候选项。
只输出一个 JSON 对象，不要使用 Markdown，不要添加“好的”等解释。字段必须为：
selectedTagCodes（字符串数组，只放 tagOptions 中有证据的 code）；
tagEvidence（对象，键为已选 code，值为 rawComplaint 中的证据原文）；
normalizedSummary（字符串）；
structuredFacts（对象，含 duration/onset/location/character 字符串，aggravatingFactors/relievingFactors/associatedSymptoms 字符串数组，activityImpact 字符串）；
riskSignals、missingQuestions、uncertainties（均为字符串数组）。
不得输出 confidence、displayName、category、source 或 confirmationStatus，这些由服务端补齐。"""},
                {"role": "user", "content": json.dumps({
                    "rawComplaint": "我肚子不舒服",
                    "ageBand": "ADULT",
                    "tagOptions": [
                        {"code": "HEADACHE", "name": "头痛", "category": "头部与神经"},
                        {"code": "ABDOMINAL_PAIN", "name": "腹痛", "category": "消化系统"},
                    ],
                }, ensure_ascii=False)},
                {"role": "assistant", "content": json.dumps({
                    "selectedTagCodes": ["ABDOMINAL_PAIN"],
                    "tagEvidence": {"ABDOMINAL_PAIN": "肚子不舒服"},
                    "normalizedSummary": "患者自述腹部不适。",
                    "structuredFacts": {
                        "duration": "", "onset": "", "location": "腹部", "character": "",
                        "aggravatingFactors": [], "relievingFactors": [],
                        "associatedSymptoms": [], "activityImpact": "",
                    },
                    "riskSignals": [], "missingQuestions": ["腹部不适从什么时候开始？"],
                    "uncertainties": ["腹部不适的具体性质尚不清楚"],
                }, ensure_ascii=False)},
                {"role": "user", "content": json.dumps({
                    "rawComplaint": "我的脚发麻",
                    "ageBand": "ADULT",
                    "tagOptions": [
                        {"code": "DIZZINESS", "name": "头晕", "category": "头部与神经"},
                        {"code": "LIMB_WEAKNESS_NUMBNESS", "name": "肢体无力或麻木", "category": "头部与神经"},
                    ],
                }, ensure_ascii=False)},
                {"role": "assistant", "content": json.dumps({
                    "selectedTagCodes": ["LIMB_WEAKNESS_NUMBNESS"],
                    "tagEvidence": {"LIMB_WEAKNESS_NUMBNESS": "脚发麻"},
                    "normalizedSummary": "患者自述足部麻木。",
                    "structuredFacts": {
                        "duration": "", "onset": "", "location": "足部", "character": "麻木",
                        "aggravatingFactors": [], "relievingFactors": [],
                        "associatedSymptoms": [], "activityImpact": "",
                    },
                    "riskSignals": [], "missingQuestions": ["足部麻木从什么时候开始？"],
                    "uncertainties": ["麻木范围及是否伴随无力尚不清楚"],
                }, ensure_ascii=False)},
                {"role": "user", "content": json.dumps(model_input, ensure_ascii=False)},
            ]}
        result = self._call(payload)
        evidence = result.pop("tagEvidence", {})
        candidate_codes = result.pop("selectedTagCodes", [])
        if not isinstance(candidate_codes, list):
            raise RuntimeError("AI_INVALID_SELECTED_TAG_CODES")
        if not isinstance(evidence, dict):
            evidence = {}
        tags = []
        seen = set()
        for candidate in candidate_codes:
            code = str(candidate).strip().upper()
            definition = available.get(code)
            if definition is None or code in seen:
                continue
            seen.add(code)
            tags.append({
                "code": definition.code,
                "displayName": definition.displayName,
                "category": definition.category,
                "source": "ai_extracted",
                "confidence": None,
                "evidenceText": str(evidence.get(code, ""))[:300],
                "confirmationStatus": "proposed",
            })
        result["extractedTags"] = tags
        return result

    def _call(self, payload: Dict) -> Dict:
        try:
            response = httpx.post(f"{settings.base_url.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {settings.api_key}"}, json=payload,
                timeout=getattr(settings, "request_timeout_seconds", 20))
            response.raise_for_status()
        except httpx.TimeoutException as exc:
            raise ProviderTimeoutError("AI_TIMEOUT") from exc
        except httpx.HTTPStatusError as exc:
            raise RuntimeError("AI_PROVIDER_HTTP_ERROR") from exc
        except httpx.RequestError as exc:
            raise RuntimeError("AI_PROVIDER_REQUEST_ERROR") from exc
        try:
            content=response.json()["choices"][0]["message"]["content"]
        except (ValueError, KeyError, IndexError, TypeError) as exc:
            raise RuntimeError("AI_INVALID_PROVIDER_RESPONSE") from exc
        return self._extract_json_object(content)

    @staticmethod
    def _extract_json_object(content: str) -> Dict:
        if not isinstance(content, str):
            raise RuntimeError("AI_INVALID_JSON_OBJECT")
        try:
            result = json.loads(content.strip())
            if isinstance(result, dict):
                return result
        except json.JSONDecodeError:
            pass
        decoder = json.JSONDecoder()
        for index, character in enumerate(content):
            if character != "{":
                continue
            try:
                result, _ = decoder.raw_decode(content[index:])
                if isinstance(result, dict):
                    return result
            except json.JSONDecodeError:
                continue
        raise RuntimeError("AI_INVALID_JSON_OBJECT")


def get_provider():
    return DeterministicFakeProvider() if settings.provider == "fake" else OpenAICompatibleProvider()

