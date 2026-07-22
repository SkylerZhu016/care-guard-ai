import random
import re
from typing import List
from .models import PreConsultInput, TriageOutput, SafetyCheckInput, SafetyCheckOutput

class MedicalAIEngine:
    """Simulated AI engine for medical pre-consultation."""

    RED_FLAG_SYMPTOMS = {
        "胸痛": {"severity_threshold": 5, "factor": "心源性疾病风险"},
        "呼吸困难": {"severity_threshold": 3, "factor": "呼吸功能不全风险"},
        "意识模糊": {"severity_threshold": 1, "factor": "神经系统急症风险"},
        "咯血": {"severity_threshold": 3, "factor": "肺部病变风险"},
        "黑便": {"severity_threshold": 4, "factor": "消化道出血风险"},
        "剧烈头痛": {"severity_threshold": 7, "factor": "脑血管意外风险"},
        "高热不退": {"severity_threshold": 7, "factor": "严重感染风险"},
    }

    RISK_KEYWORDS = {
        "高血压": 2, "糖尿病": 2, "冠心病": 3, "心衰": 4,
        "脑卒中": 4, "肾功能不全": 3, "肝病": 2, "肿瘤": 3,
    }

    SAFETY_BLOCKLIST = [
        "治愈", "保证", "100%", "绝无副作用", "代替医生",
        "我给你开药", "诊断你就是", "你得了",
    ]

    def triage(self, input_data: PreConsultInput) -> TriageOutput:
        risk_score = 0
        red_flags = []
        risk_factors = []

        for symptom in input_data.symptoms:
            sname = symptom.symptom_name
            severity = symptom.severity or 5
            if sname in self.RED_FLAG_SYMPTOMS:
                info = self.RED_FLAG_SYMPTOMS[sname]
                if severity >= info["severity_threshold"]:
                    red_flags.append(f"{sname}(程度{severity}) - {info['factor']}")
                    risk_score += min(severity, 7)

        if input_data.patient_history:
            for kw, score in self.RISK_KEYWORDS.items():
                if kw in input_data.patient_history:
                    risk_factors.append(f"既往史：{kw}")
                    risk_score += score

        if input_data.patient_age > 65:
            risk_factors.append("年龄>65岁")
            risk_score += 1
        if input_data.patient_age > 80:
            risk_factors.append("高龄(>80岁)")
            risk_score += 1

        complaint = input_data.chief_complaint
        if any(flag in complaint for flag in ["胸痛", "呼吸困难", "意识不清"]):
            if "胸痛" in complaint:
                red_flags.append("主诉包含胸痛 - 需排除ACS")
            if "呼吸困难" in complaint:
                red_flags.append("主诉包含呼吸困难 - 需评估呼吸功能")
            risk_score += 3

        if risk_score >= 8:
            risk_level = "CRITICAL"
        elif risk_score >= 5:
            risk_level = "HIGH"
        elif risk_score >= 3:
            risk_level = "MEDIUM"
        else:
            risk_level = "LOW"

        if risk_level == "CRITICAL":
            recommendations = "建议立即转诊上级医院急诊。监测生命体征，保持呼吸道通畅。"
        elif risk_level == "HIGH":
            recommendations = "建议尽快安排医生面诊。完善相关检查，密切观察病情变化。"
        elif risk_level == "MEDIUM":
            recommendations = "建议预约医生进一步评估。必要时进行针对性检查。"
        else:
            recommendations = "可进行常规门诊就诊。注意休息，如有加重及时就医。"

        safety_report = self._safety_check(complaint)
        safety_checked = True

        if not risk_factors:
            risk_factors.append("未见明显高危因素")

        return TriageOutput(
            risk_score=min(risk_score, 10),
            risk_level=risk_level,
            risk_factors=risk_factors,
            red_flags=red_flags if red_flags else ["未发现红旗症状"],
            recommendations=recommendations,
            safety_checked=safety_checked,
            safety_report=safety_report,
        )

    def _safety_check(self, text: str) -> str:
        issues = []
        for phrase in self.SAFETY_BLOCKLIST:
            if phrase in text:
                issues.append(f"检测到不当表述：{phrase}")

        diagnostic_patterns = ["诊断", "确诊", "你就是得了", "一定是"]
        for pattern in diagnostic_patterns:
            if pattern in text:
                issues.append(f"检测到潜在诊断性表述：{pattern}")

        if issues:
            return "安全警告：" + "；".join(issues)
        return "安全审核通过"

    def safety_check_text(self, input_text: SafetyCheckInput) -> SafetyCheckOutput:
        text = input_text.text.lower()

        injection_patterns = [
            "ignore previous", "ignore all", "forget your",
            "you are not", "你不需要", "忽略之前", "忘记你的身份",
            "system prompt", "你是一个", "扮演",
        ]
        for pattern in injection_patterns:
            if pattern in text:
                return SafetyCheckOutput(
                    is_safe=False,
                    alert_type="PROMPT_INJECTION",
                    severity="CRITICAL",
                    reason=f"检测到提示词攻击模式：{pattern}",
                )

        privacy_patterns = [
            r"\d{17}[\dXx]",  # ID number
            r"1[3-9]\d{9}",   # Phone
            r"\d{6}@",        # Email-like
        ]
        for pattern in privacy_patterns:
            if re.search(pattern, text):
                return SafetyCheckOutput(
                    is_safe=False,
                    alert_type="PRIVACY_LEAK",
                    severity="WARNING",
                    reason="检测到可能的隐私信息泄露",
                )

        diagnosis_claims = ["我诊断", "我认为是", "确诊为", "肯定是"]
        for claim in diagnosis_claims:
            if claim in text:
                return SafetyCheckOutput(
                    is_safe=False,
                    alert_type="UNAUTHORIZED_DIAGNOSIS",
                    severity="CRITICAL",
                    reason=f"检测到越权诊断表述：{claim}",
                )

        return SafetyCheckOutput(
            is_safe=True,
            alert_type=None,
            severity=None,
            reason="内容安全",
        )

engine = MedicalAIEngine()