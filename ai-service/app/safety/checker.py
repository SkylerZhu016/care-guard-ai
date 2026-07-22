"""确定性安全检查器 —— 一票否决，不依赖 LLM"""
import re
from typing import Optional
from app.clients.llm import DIAGNOSIS_WORDS, PRESCRIPTION_WORDS, INJECTION_PATTERNS, detect_injection

DISCLAIMER_TEXT = "不能替代医生诊断"

# PII 正则
PII_PATTERNS = [
    (r"1[3-9]\d{9}", "手机号"),
    (r"\d{17}[\dXx]", "身份证号"),
]


def check_output(content: str, risk_points: list, rule_hits: list, has_citations: bool) -> dict:
    """对 AI 输出做确定性安全检查。
    返回 {verdict: PASS/FAIL, issues: [{type, detail, severity}]}
    """
    issues = []

    # 1. 诊断词
    for word in DIAGNOSIS_WORDS:
        if word in content:
            issues.append({"type": "DIAGNOSIS", "detail": f"输出包含确定性诊断词：{word}", "severity": "HIGH"})

    # 2. 处方词
    for word in PRESCRIPTION_WORDS:
        if word in content:
            issues.append({"type": "PRESCRIPTION", "detail": f"输出包含处方/用药建议：{word}", "severity": "HIGH"})

    # 3. 免责声明
    if DISCLAIMER_TEXT not in content:
        issues.append({"type": "NO_DISCLAIMER", "detail": "输出缺少免责声明", "severity": "MEDIUM"})

    # 4. 红旗症状覆盖
    critical_rules = [r for r in rule_hits if r.get("risk_level") == "CRITICAL"]
    if critical_rules:
        mentioned = content.lower()
        for r in critical_rules:
            if r.get("rule_code", "").lower() not in mentioned and r.get("message", "")[:10] not in content:
                # 红旗规则未在风险点中被提及
                risk_point_texts = " ".join([rp.get("point", "") + rp.get("basis", "") for rp in risk_points])
                if r.get("message", "")[:8] not in risk_point_texts:
                    issues.append({
                        "type": "REDFLAG_MISSED",
                        "detail": f"红旗症状未被充分覆盖：{r.get('rule_name')}",
                        "severity": "HIGH",
                    })

    # 5. 引用存在性
    if risk_points and not has_citations:
        high_points = [rp for rp in risk_points if rp.get("severity") in ("HIGH", "CRITICAL")]
        if high_points:
            issues.append({"type": "NO_CITATION", "detail": "高风险结论缺少指南引用", "severity": "MEDIUM"})

    # 6. PII 泄露
    for pat, name in PII_PATTERNS:
        if re.search(pat, content):
            issues.append({"type": "PII_LEAK", "detail": f"输出可能包含敏感信息：{name}", "severity": "HIGH"})

    verdict = "FAIL" if any(i["severity"] == "HIGH" for i in issues) else "PASS"
    return {"verdict": verdict, "issues": issues, "sanitized": False}


def check_input_injection(text: str) -> Optional[str]:
    """输入侧注入检测"""
    return detect_injection(text)
