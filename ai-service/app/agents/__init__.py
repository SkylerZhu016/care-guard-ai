"""五段 Agent —— 每段调用 LLM 并校验输出 Schema"""
import json
import logging
from app.clients.llm import llm_client, LLMError, extract_json
from app.schemas import (
    SymptomExtractionSchema, RiskAnalysisSchema, SafetyReviewSchema, ReviewSummarySchema,
)
from pydantic import ValidationError

logger = logging.getLogger(__name__)

PROMPTS = {
    "STRUCTURE": "你是医疗信息结构化助手。从患者预问诊文本中提取结构化症状信息，严格输出指定 JSON Schema。不得给出诊断建议。必须标注信息缺失项和置信度。",
    "RETRIEVE": "根据结构化症状，生成 2-4 个用于检索公开医疗指南的关键词组合。仅输出关键词列表 JSON。",
    "RISK": "你是医疗风险分析助手。基于规则命中结果、结构化症状和检索到的指南证据，列出需要关注的风险点。每个风险点必须挂接引用 id；无引用时 severity 不得高于 MEDIUM 并标注证据不足。禁止输出确定性诊断。",
    "SAFETY": "你是安全审查助手。检查内容是否包含确定性诊断、处方建议、是否缺少免责声明、是否遗漏红旗症状、引用是否完整。输出审查结论和问题清单。确定性检查规则优先于本 Prompt。",
    "SUMMARY": "你是预问诊汇总助手。为医务人员生成结构化审核摘要，包括主诉、症状表、风险点、依据引用和建议关注方向。必须以免责声明结尾。不得替代医生做出最终结论。",
}


def run_structure(form_data: dict) -> tuple[dict, int]:
    """结构化 Agent：预问诊表单 → 结构化症状 JSON"""
    text = _form_to_text(form_data)
    prompt = PROMPTS["STRUCTURE"]
    raw, tokens = llm_client.chat(prompt, text)
    try:
        data = extract_json(raw)
        SymptomExtractionSchema(**data)  # 校验
        return data, tokens
    except (ValidationError, json.JSONDecodeError) as e:
        logger.warning(f"STRUCTURE 输出校验失败: {e}，尝试修复")
        # 降级：简单结构化
        from app.clients.llm import MockLLM
        raw2, tokens2 = MockLLM._gen_structure(text)
        data = json.loads(raw2)
        return data, tokens + tokens2


def run_retrieval(structured: dict) -> tuple[list[str], int]:
    """检索词构造 Agent"""
    text = json.dumps(structured, ensure_ascii=False)
    raw, tokens = llm_client.chat(PROMPTS["RETRIEVE"], text, max_tokens=256)
    try:
        data = extract_json(raw)
        queries = data.get("queries", [])
        if not queries:
            queries = [structured.get("chief_complaint", "常见症状")[:50]]
        return queries, tokens
    except (json.JSONDecodeError, KeyError):
        return [structured.get("chief_complaint", "")[:50]], tokens


def run_risk(structured: dict, rule_hits: list, citations: list) -> tuple[dict, int]:
    """风险分析 Agent"""
    context = json.dumps({
        "chief_complaint": structured.get("chief_complaint"),
        "symptoms": structured.get("symptoms", []),
        "severity": structured.get("severity"),
        "accompanying": structured.get("accompanying", []),
        "special_group": structured.get("special_group", "NONE"),
        "rule_hits": rule_hits,
        "citations": [{"id": str(c.get("chunk_id")), "title": c.get("title"), "section": c.get("section")} for c in citations],
    }, ensure_ascii=False)
    raw, tokens = llm_client.chat(PROMPTS["RISK"], context)
    try:
        data = extract_json(raw)
        RiskAnalysisSchema(**data)
        return data, tokens
    except (ValidationError, json.JSONDecodeError):
        # 降级：基于规则的风险提示
        risk_points = []
        for rh in rule_hits:
            risk_points.append({
                "point": rh.get("message", ""),
                "basis": f"规则 {rh.get('rule_code')} 命中",
                "citation_ids": [],
                "severity": rh.get("risk_level", "LOW"),
            })
        if not risk_points:
            risk_points.append({"point": "当前症状风险较低", "basis": "一般评估", "citation_ids": [], "severity": "LOW"})
        return {
            "schema_version": "1.0",
            "risk_level": max([rh.get("risk_level", "LOW") for rh in rule_hits], default="LOW"),
            "risk_summary": "基于规则引擎的风险评估（AI 分析降级）",
            "risk_points": risk_points,
            "questions_for_doctor": [],
            "red_flags_noticed": [],
        }, tokens


def run_safety(content: str, risk_points: list, rule_hits: list, has_citations: bool) -> tuple[dict, int]:
    """安全审查 Agent：确定性 checker + LLM 复核"""
    # 确定性检查（一票否决）
    from app.safety.checker import check_output
    det = check_output(content, risk_points, rule_hits, has_citations)

    # LLM 复核（仅补充，不覆盖确定性结论）
    try:
        raw, tokens = llm_client.chat(PROMPTS["SAFETY"], content[:4000], max_tokens=512)
        llm_review = extract_json(raw)
        # 合并 issues：确定性优先
        merged_issues = det["issues"]
        for li in llm_review.get("issues", []):
            if not any(i["type"] == li.get("type") for i in merged_issues):
                merged_issues.append(li)
        verdict = "FAIL" if det["verdict"] == "FAIL" else llm_review.get("verdict", "PASS")
        return {"verdict": verdict, "issues": merged_issues, "sanitized": False}, tokens
    except Exception:
        return det, 0


def run_summary(structured: dict, risk_analysis: dict, citations: list) -> tuple[dict, int]:
    """汇总 Agent：生成医生审核用摘要"""
    context = json.dumps({
        **structured,
        "risk_level": risk_analysis.get("risk_level"),
        "risk_points": risk_analysis.get("risk_points", []),
        "citations": [{"chunk_id": c.get("chunk_id"), "title": c.get("title"), "section": c.get("section")} for c in citations],
    }, ensure_ascii=False)
    raw, tokens = llm_client.chat(PROMPTS["SUMMARY"], context)
    try:
        data = extract_json(raw)
        # 强制免责声明
        data["disclaimer"] = "本内容由 AI 生成，仅供教学参考，不能替代医生诊断。"
        ReviewSummarySchema(**data)
        return data, tokens
    except (ValidationError, json.JSONDecodeError):
        from app.clients.llm import MockLLM
        raw2, tokens2 = MockLLM._gen_summary(context)
        return json.loads(raw2), tokens + tokens2


def _form_to_text(form_data: dict) -> str:
    """将表单 JSON 转为自然语言文本"""
    if not form_data:
        return ""
    parts = []
    if form_data.get("chiefComplaint"):
        parts.append(f"主诉：{form_data['chiefComplaint']}")
    if form_data.get("onsetTime"):
        parts.append(f"起病时间：{form_data['onsetTime']}")
    if form_data.get("duration"):
        parts.append(f"持续时间：{form_data['duration']}")
    if form_data.get("severity"):
        parts.append(f"严重程度：{form_data['severity']}")
    if form_data.get("accompanying"):
        parts.append(f"伴随症状：{'、'.join(form_data['accompanying'])}")
    if form_data.get("triggers"):
        parts.append(f"诱因：{form_data['triggers']}")
    if form_data.get("pastHistory"):
        parts.append(f"既往史：{form_data['pastHistory']}")
    if form_data.get("allergyHistory"):
        parts.append(f"过敏史：{form_data['allergyHistory']}")
    if form_data.get("medication"):
        parts.append(f"用药：{form_data['medication']}")
    basic = form_data.get("basic", {})
    if basic.get("specialGroup") and basic["specialGroup"] != "NONE":
        parts.append(f"特殊人群：{basic['specialGroup']}")
    if form_data.get("supplement"):
        parts.append(f"补充：{form_data['supplement']}")
    return "\n".join(parts)
