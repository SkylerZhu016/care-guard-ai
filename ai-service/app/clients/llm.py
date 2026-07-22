"""LLM 客户端 —— OpenAI 兼容 + Mock 降级"""
import json
import re
import time
import logging
from typing import Optional
import httpx
from app.core.config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()

# 诊断词黑名单（安全审查用）
DIAGNOSIS_WORDS = ["确诊", "诊断为", "你患了", "你得了", "可以确诊", "明确诊断"]
PRESCRIPTION_WORDS = ["处方", "每日三次", "口服mg", "静脉注射", "肌肉注射", "开具", "建议服用"]
INJECTION_PATTERNS = [
    r"忽略.{0,10}(指令|提示|规则)",
    r"你现在是.{0,10}(医生|管理员|系统)",
    r"输出.{0,10}(系统|初始).{0,10}(提示|指令|prompt)",
    r"假装.{0,10}(你是|你是)",
    r"jailbreak", r"越狱",
]


def detect_injection(text: str) -> Optional[str]:
    """检测提示词注入特征，返回匹配的模式描述"""
    low = text.lower()
    for pat in INJECTION_PATTERNS:
        if re.search(pat, text, re.IGNORECASE):
            return f"命中注入特征: {pat}"
    return None


def extract_json(text: str) -> dict:
    """从 LLM 输出中提取 JSON（支持 ```json 围栏）"""
    # 尝试直接解析
    text = text.strip()
    if text.startswith("```"):
        m = re.search(r"```(?:json)?\s*(.*?)```", text, re.DOTALL)
        if m:
            text = m.group(1).strip()
    # 尝试找到第一个 { 和最后一个 }
    start = text.find("{")
    end = text.rfind("}")
    if start != -1 and end != -1 and end > start:
        text = text[start : end + 1]
    return json.loads(text)


class LLMClient:
    """OpenAI 兼容 LLM 客户端"""

    def __init__(self):
        self.model = settings.LLM_MODEL
        self.timeout = settings.LLM_TIMEOUT
        self.max_retries = settings.LLM_MAX_RETRIES

    def chat(self, system_prompt: str, user_content: str, max_tokens: int = 2048) -> tuple[str, int]:
        """调用 LLM，返回 (输出文本, 估算tokens)"""
        if settings.MOCK_LLM or not settings.LLM_API_KEY:
            return MockLLM.generate(system_prompt, user_content)

        headers = {"Authorization": f"Bearer {settings.LLM_API_KEY}", "Content-Type": "application/json"}
        payload = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system_prompt[:2000]},
                {"role": "user", "content": user_content[: settings.LLM_MAX_INPUT_CHARS]},
            ],
            "max_tokens": min(max_tokens, settings.LLM_MAX_TOKENS),
            "temperature": 0.2,
        }

        last_err = None
        for attempt in range(self.max_retries + 1):
            try:
                with httpx.Client(timeout=self.timeout) as client:
                    resp = client.post(
                        f"{settings.LLM_API_BASE}/chat/completions", headers=headers, json=payload
                    )
                    resp.raise_for_status()
                    data = resp.json()
                    text = data["choices"][0]["message"]["content"]
                    tokens = data.get("usage", {}).get("total_tokens", len(text) // 3)
                    return text, tokens
            except Exception as e:
                last_err = e
                logger.warning(f"LLM 调用失败(第{attempt+1}次): {e}")
                if attempt < self.max_retries:
                    time.sleep(2 ** attempt)

        raise LLMError(f"LLM 调用失败（重试{self.max_retries}次）: {last_err}")


class LLMError(Exception):
    pass


class MockLLM:
    """确定性 Mock LLM —— 离线演示与评测基线"""

    SYMptom_KEYWORDS = {
        "头痛": ("头痛", "头部", None, None),
        "头晕": ("头晕", "头部", None, None),
        "发热": ("发热", "全身", None, None),
        "发烧": ("发热", "全身", None, None),
        "高热": ("发热", "全身", "SEVERE", None),
        "咳嗽": ("咳嗽", "呼吸道", None, None),
        "咳痰": ("咳痰", "呼吸道", None, None),
        "胸痛": ("胸痛", "胸部", None, None),
        "胸闷": ("胸闷", "胸部", None, None),
        "心悸": ("心悸", "胸部", None, None),
        "呼吸困难": ("呼吸困难", "呼吸系统", None, None),
        "气促": ("呼吸困难", "呼吸系统", None, None),
        "气短": ("呼吸困难", "呼吸系统", None, None),
        "腹痛": ("腹痛", "腹部", None, None),
        "腹泻": ("腹泻", "消化系统", None, None),
        "恶心": ("恶心", "消化系统", None, None),
        "呕吐": ("呕吐", "消化系统", None, None),
        "乏力": ("乏力", "全身", None, None),
        "皮疹": ("皮疹", "皮肤", None, None),
        "红疹": ("皮疹", "皮肤", None, None),
        "咽痛": ("咽痛", "咽喉", None, None),
        "流涕": ("流涕", "鼻部", None, None),
        "鼻塞": ("鼻塞", "鼻部", None, None),
        "出血": ("出血", None, None, None),
        "阴道出血": ("阴道出血", "生殖系统", None, None),
        "意识模糊": ("意识障碍", "神经系统", "SEVERE", None),
        "昏迷": ("意识障碍", "神经系统", "SEVERE", None),
        "晕厥": ("意识障碍", "神经系统", "SEVERE", None),
        "抽搐": ("抽搐", "神经系统", "SEVERE", None),
        "出汗": ("出汗", "全身", None, None),
        "食欲不振": ("食欲不振", "消化系统", None, None),
        "肌肉酸痛": ("肌肉酸痛", "肌肉", None, None),
    }

    @staticmethod
    def generate(system_prompt: str, user_content: str) -> tuple[str, int]:
        """根据 system_prompt 类型生成确定性输出"""
        prompt_lower = system_prompt.lower()

        if "安全" in system_prompt or "审查" in system_prompt or "safety" in prompt_lower:
            return MockLLM._gen_safety(user_content)
        elif "检索" in system_prompt and "词" in system_prompt:
            return MockLLM._gen_retrieval(user_content)
        elif "风险" in system_prompt or "risk" in prompt_lower:
            return MockLLM._gen_risk(user_content)
        elif "汇总" in system_prompt or "摘要" in system_prompt or "summary" in prompt_lower:
            return MockLLM._gen_summary(user_content)
        elif "结构化" in system_prompt or "structure" in prompt_lower:
            return MockLLM._gen_structure(user_content)
        else:
            return json.dumps({"result": "ok"}, ensure_ascii=False), 10

    @staticmethod
    def _gen_structure(text: str) -> tuple[str, int]:
        symptoms = []
        found = set()
        for kw, (name, part, sev, _) in MockLLM.SYMptom_KEYWORDS.items():
            if kw in text and name not in found:
                symptoms.append({"name": name, "body_part": part, "severity": sev, "duration": None})
                found.add(name)

        chief = text[:200] if text else "未提供主诉"
        missing = []
        if not any(k in text for k in ["时间", "天", "小时", "周", "月"]):
            missing.append("起病时间")

        result = {
            "schema_version": "1.0",
            "chief_complaint": chief,
            "symptoms": symptoms or [{"name": "未识别到明确症状", "body_part": None, "severity": None, "duration": None}],
            "onset_time": None,
            "duration_text": None,
            "triggers": None,
            "relief_factors": None,
            "aggravating_factors": None,
            "accompanying": [],
            "denied": [],
            "past_history": [],
            "allergies": [],
            "medications": [],
            "special_group": "NONE",
            "missing_fields": missing,
            "confidence": 0.85 if symptoms else 0.4,
        }
        return json.dumps(result, ensure_ascii=False), len(text) // 3 + 50

    @staticmethod
    def _gen_retrieval(text: str) -> tuple[str, int]:
        # 从文本中提取关键词
        queries = []
        for kw in ["胸痛", "发热", "咳嗽", "头痛", "腹痛", "高血压", "糖尿病", "孕期", "儿童", "皮疹"]:
            if kw in text:
                queries.append(kw)
        if not queries:
            queries = ["常见症状", "基层诊疗"]
        result = {"schema_version": "1.0", "queries": queries[:4], "top_k": 5}
        return json.dumps(result, ensure_ascii=False), 20

    @staticmethod
    def _gen_risk(text: str) -> tuple[str, int]:
        try:
            data = json.loads(text)
        except Exception:
            data = {}

        risk_points = []
        risk_level = "LOW"
        chief = data.get("chief_complaint", "")
        symptoms_text = chief + " " + " ".join(data.get("accompanying", []))

        if "胸痛" in symptoms_text and any(k in symptoms_text for k in ["呼吸困难", "气促", "出汗"]):
            risk_points.append({"point": "胸痛伴呼吸困难，提示可能急性心血管事件", "basis": "指南：胸痛伴呼吸困难属红旗症状", "citation_ids": ["2-1"], "severity": "CRITICAL"})
            risk_level = "CRITICAL"
        if "发热" in symptoms_text and data.get("severity") == "SEVERE":
            risk_points.append({"point": "高热不退，需排查严重感染", "basis": "指南：高热不退需就诊", "citation_ids": ["1-2"], "severity": "HIGH"})
            risk_level = max(risk_level, "HIGH") if risk_level != "CRITICAL" else "CRITICAL"
        if not risk_points:
            risk_points.append({"point": "当前症状风险较低，建议观察", "basis": "一般评估", "citation_ids": [], "severity": "LOW"})

        result = {
            "schema_version": "1.0",
            "risk_level": risk_level,
            "risk_summary": f"基于症状分析，风险等级为{risk_level}。",
            "risk_points": risk_points,
            "questions_for_doctor": ["请核实症状持续时间与诱因"],
            "red_flags_noticed": [rp["point"] for rp in risk_points if rp["severity"] in ("HIGH", "CRITICAL")],
        }
        return json.dumps(result, ensure_ascii=False), 80

    @staticmethod
    def _gen_safety(text: str) -> tuple[str, int]:
        # Mock 安全审查：默认 PASS（确定性 checker 会独立做硬检查）
        result = {"schema_version": "1.0", "verdict": "PASS", "issues": [], "sanitized": False}
        return json.dumps(result, ensure_ascii=False), 15

    @staticmethod
    def _gen_summary(text: str) -> tuple[str, int]:
        try:
            data = json.loads(text)
        except Exception:
            data = {}

        result = {
            "schema_version": "1.0",
            "chief_complaint": data.get("chief_complaint", ""),
            "symptom_table": data.get("symptoms", []),
            "risk_level": data.get("risk_level", "LOW"),
            "risk_points": data.get("risk_points", []),
            "citations": [],
            "suggested_focus": ["请核实主诉与起病时间", "关注红旗症状"],
            "disclaimer": "本内容由 AI 生成，仅供教学参考，不能替代医生诊断。",
        }
        return json.dumps(result, ensure_ascii=False), 100


# 单例
llm_client = LLMClient()
