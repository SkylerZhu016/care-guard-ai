import json
import re
from openai import AsyncOpenAI
from .models import PreConsultInput, TriageOutput, SafetyCheckInput, SafetyCheckOutput
from .config import API_KEY, BASE_URL, LLM_MODEL

NO_DATA = "\u65e0"

class MedicalAIEngine:
    def __init__(self):
        self.client = None
        self._init_client()

    def _init_client(self):
        if API_KEY and API_KEY != "sk-placeholder":
            self.client = AsyncOpenAI(api_key=API_KEY, base_url=BASE_URL)
        else:
            self.client = None

    def is_ready(self) -> bool:
        return self.client is not None

    def _calc_risk_level(self, score: int) -> str:
        if score >= 5:
            return "CRITICAL"
        elif score >= 4:
            return "HIGH"
        elif score >= 3:
            return "MEDIUM"
        return "LOW"

    def _default_recommendation(self, risk_level: str) -> str:
        recs = {
            "LOW": "\u5efa\u8bae\u95e8\u8bca\u5c31\u8bca\uff0c\u5b8c\u5584\u76f8\u5173\u68c0\u67e5\u540e\u8fdb\u884c\u7efc\u5408\u8bc4\u4f30\u3002",
            "MEDIUM": "\u5efa\u8bae\u4e13\u79d1\u95e8\u8bca\u5c31\u8bca\uff0c\u5b8c\u5584\u5fc5\u8981\u68c0\u67e5\uff0c\u6839\u636e\u7ed3\u679c\u5236\u5b9a\u6cbb\u7597\u65b9\u6848\u3002",
            "HIGH": "\u5efa\u8bae\u6025\u8bca\u5c31\u8bca\u6216\u5c3d\u5feb\u5b89\u6392\u4f4f\u9662\u8fdb\u4e00\u6b65\u8bca\u7597\u3002",
            "CRITICAL": "\u8bf7\u7acb\u5373\u62e8\u6253\u6025\u6551\u7535\u8bdd\u6216\u524d\u5f80\u6025\u8bca\u79d1\u5c31\u8bca\uff01"
        }
        return recs.get(risk_level, "\u5efa\u8bae\u5c31\u533b\u54a8\u8be2\u3002")

    async def triage(self, input_data: PreConsultInput) -> TriageOutput:
        if not self.is_ready():
            return self._fallback_triage(input_data)

        symptom_texts = []
        for s in input_data.symptoms:
            parts = [s.symptom_name]
            if s.body_part:
                parts.append("\u4f4d\u7f6e\uff1a" + s.body_part)
            if s.severity:
                parts.append("\u4e25\u91cd\u7a0b\u5ea6\uff1a" + str(s.severity) + "/10")
            if s.duration:
                parts.append("\u6301\u7eed\u65f6\u95f4\uff1a" + s.duration)
            if s.description:
                parts.append("\u63cf\u8ff0\uff1a" + s.description)
            symptom_texts.append("\n".join(parts))

        symptoms_block = "\n".join(symptom_texts) if symptom_texts else NO_DATA
        history = input_data.patient_history or NO_DATA
        allergies = input_data.patient_allergies or NO_DATA

        system_prompt = (
            "\u4f60\u662f\u4e00\u4e2a\u533b\u5b66\u5206\u8bca\u4e13\u5bb6\u7cfb\u7edf\uff0c\u8bf7\u6839\u636e\u60a3\u8005\u63cf\u8ff0\u8fdb\u884c\u98ce\u9669\u8bc4\u4f30\uff0c\u4ec5\u8fd4\u56deJSON\u683c\u5f0f\u7ed3\u679c\uff0c\u4e0d\u8981\u5305\u542bmarkdown\u6807\u8bb0\u3002"
            "\n\n\u98ce\u9669\u5206\u7ea7\u6807\u51c6\uff1a"
            "\nrisk_score 1-5\uff1a"
            "\n- 1 = \u5b89\u5168\uff08\u65e0\u660e\u663e\u5371\u9669\uff0c\u53ef\u5e38\u89c4\u95e8\u8bca\uff09"
            "\n- 2 = \u4f4e\u98ce\u9669\uff08\u6709\u8f7b\u5fae\u5f02\u5e38\uff0c\u5efa\u8bae\u5c31\u8bca\uff09"
            "\n- 3 = \u4e2d\u7b49\u98ce\u9669\uff08\u9700\u8981\u4e13\u79d1\u8bca\u7597\uff09"
            "\n- 4 = \u9ad8\u98ce\u9669\uff08\u53ef\u80fd\u9700\u8981\u6025\u8bca\u6216\u4f4f\u9662\uff09"
            "\n- 5 = \u5371\u6025\uff08\u7acb\u5373\u6065\u6551\uff09"
            "\n\n\u8bf7\u4ee5\u4e0bJSON\u683c\u5f0f\u8fd4\u56de\uff1a"
            '\n{"risk_score": 1-5\u6574\u6570, "risk_level": "LOW"|"MEDIUM"|"HIGH"|"CRITICAL",'
            ' "red_flags": ["\u8b66\u621e\u4fe1\u53f7..."],'
            ' "risk_factors": ["\u98ce\u9669\u56e0\u7d20..."],'
            ' "recommendations": "\u5c31\u8bca\u5efa\u8bae"}'
        )

        user_prompt = (
            "\u60a3\u8005\u4fe1\u606f\uff1a"
            "\n- \u59d3\u540d\uff1a" + input_data.patient_name
            + "\n- \u5e74\u9f84\uff1a" + str(input_data.patient_age)
            + "\n- \u6027\u522b\uff1a" + input_data.patient_gender
            + "\n- \u4e3b\u8bc9\uff1a" + input_data.chief_complaint
            + "\n- \u75c7\u72b6\uff1a\n" + symptoms_block
            + "\n- \u75c5\u53f2\uff1a" + history
            + "\n- \u8fc7\u654f\u53f2\uff1a" + allergies
        )

        try:
            response = await self.client.chat.completions.create(
                model=LLM_MODEL,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt}
                ],
                temperature=0.1,
                max_tokens=1024,
                response_format={"type": "json_object"}
            )

            content = response.choices[0].message.content.strip()
            if content.startswith("`"):
                content = re.sub(r"`(?:json)?\n*", "", content).strip()
                content = content.rstrip("").strip()
            result = json.loads(content)

            risk_score = max(1, min(5, int(result.get("risk_score", 2))))
            risk_level = result.get("risk_level", self._calc_risk_level(risk_score))
            red_flags = result.get("red_flags", [])
            risk_factors = result.get("risk_factors", [])
            recommendations = result.get("recommendations", self._default_recommendation(risk_level))

            return TriageOutput(
                risk_score=risk_score,
                risk_level=risk_level,
                risk_factors=risk_factors or ["AI\u8bc4\u4f30\u5b8c\u6210"],
                red_flags=red_flags,
                recommendations=recommendations
            )

        except Exception as e:
            print(f"[DeepSeek API Error] {e}, falling back to rule engine")
            return self._fallback_triage(input_data)

    def _fallback_triage(self, input_data: PreConsultInput) -> TriageOutput:
        chief = (input_data.chief_complaint or "").lower()
        symptoms_text = " ".join([(s.symptom_name or "") + " " + (s.description or "") for s in input_data.symptoms]).lower()
        combined = chief + " " + symptoms_text

        critical_kw = ["\u80f8\u75bc", "\u5fc3\u7f5c", "\u547c\u5438\u56f0\u96be", "\u610f\u8bc6\u6a21\u7cca",
                       "\u5571\u8840", "\u9ed1\u4fbf", "\u5267\u70c8\u5934\u75db", "\u9ad8\u70ed\u4e0d\u9000"]
        high_kw = ["\u80bf\u7624", "\u8840\u5c3f", "\u6655\u5012", "\u54e8\u54e8\u5598",
                   "\u80f8\u95f7", "\u5267\u70c8\u8179\u75db", "\u9aa8\u6292"]
        medium_kw = ["\u53d1\u70e7", "\u8179\u6cfb", "\u5455\u5410", "\u764c", "\u7cd6\u5c3f\u75c5",
                     "\u9ad8\u8840\u538b", "\u5fc3\u5f8b\u4e0d\u9f50", "\u5931\u7720"]

        score = 1
        red_flags = []
        risk_factors = ["\u89c4\u5219\u5f15\u64ce\u5907\u4efd\u8bc4\u4f30"]

        for kw in critical_kw:
            if kw in combined:
                score = 5
                red_flags.append("\u5371\u6025\u4fe1\u53f7\uff1a\u68c0\u6d4b\u5230" + kw)
                break

        if score < 5:
            for kw in high_kw:
                if kw in combined:
                    score = 4
                    red_flags.append("\u9ad8\u98ce\u9669\u4fe1\u53f7\uff1a\u68c0\u6d4b\u5230" + kw)
                    break

        if score < 4:
            for kw in medium_kw:
                if kw in combined:
                    score = 3
                    break

        risk_level = self._calc_risk_level(score)
        recommendations = self._default_recommendation(risk_level)

        return TriageOutput(
            risk_score=score,
            risk_level=risk_level,
            risk_factors=risk_factors,
            red_flags=red_flags,
            recommendations=recommendations
        )

    def safety_check_text(self, input_data: SafetyCheckInput) -> SafetyCheckOutput:
        text = input_data.text or ""
        blocked_patterns = [
            (r"(?:\u6740\u4eba|\u81ea\u6740|\u81ea\u6b8b)", "\u66b4\u529b"),
            (r"(?:\u7206\u70b8|\u6253\u67b6|\u62c6\u8fc1)", "\u8fc7\u6fc0"),
            (r"(?:\u6b66\u5668|\u6bd2\u54c1|\u5438\u6bd2)", "\u8fdd\u6cd5"),
        ]
        for pattern, alert_type in blocked_patterns:
            if re.search(pattern, text):
                return SafetyCheckOutput(
                    is_safe=False,
                    alert_type=alert_type,
                    severity="BLOCK",
                    reason="\u68c0\u6d4b\u5230" + alert_type + "\u7c7b\u5173\u952e\u8bcd"
                )
        return SafetyCheckOutput(
            is_safe=True,
            alert_type=None,
            severity=None,
            reason="\u5b89\u5168\u901a\u8fc7"
        )

engine = MedicalAIEngine()