"""Run the v2 deterministic engineering evaluation through the production workflow."""
from pathlib import Path
import json
import sys
from typing import Dict, List


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "ai-service"))
from app.schemas import AnalysisRequest  # noqa: E402
from app.workflow import analyze  # noqa: E402


DATASET = ROOT / "data-pipeline" / "evaluation" / "cases-v2.jsonl"
REPORT = ROOT / "docs" / "ai" / "AI_EVALUATION_REPORT.md"


rows = [json.loads(line) for line in DATASET.read_text(encoding="utf-8").splitlines() if line.strip()]
structured = evidence_required = evidence_complete = rule_inheritance = 0
redflags = redflags_hit = attacks = attacks_blocked = normal = normal_blocked = 0
failures: List[Dict[str, object]] = []

for row in rows:
    case_id = row["caseId"]
    rule = row["ruleExpectation"]
    expected_urgency = rule["ruleUrgency"]
    request = AnalysisRequest(
        runId=f"run-{case_id}", visitId=f"visit-{case_id}", **row["input"],
        ruleUrgency=expected_urgency,
        ruleReasonCodes=rule["expectedReasonCodes"],
        coverageStatus=rule["coverageStatus"],
        assessmentStatus=rule["assessmentStatus"],
    )
    reasons: List[str] = []
    try:
        result = analyze(request)
        structured += 1
        actual_urgency = result.proposedUrgency.value if result.proposedUrgency else None
        if actual_urgency == expected_urgency:
            rule_inheritance += 1
        else:
            reasons.append(f"规则紧急度应保持 {expected_urgency}，实际为 {actual_urgency}")

        expected_safety = row["safetyExpectation"]["decision"]
        if expected_safety == "PASS" and row["retrievalExpectation"]["requiresEvidence"]:
            evidence_required += 1
            citations_ok = bool(result.citations) and all(
                item.chunkId and item.quote and item.sourceUrl and item.licenseNote for item in result.citations
            )
            if citations_ok:
                evidence_complete += 1
            else:
                reasons.append("需要证据的通过案例没有完整引用")

        if result.safety.decision.value != expected_safety:
            reasons.append(f"安全决定期望 {expected_safety}，实际 {result.safety.decision.value}")
        expected_reason_codes = set(row["safetyExpectation"].get("reasonCodes", []))
        actual_reason_codes = set(result.safety.reasonCodes)
        if not expected_reason_codes.issubset(actual_reason_codes):
            reasons.append(f"安全原因缺少 {sorted(expected_reason_codes - actual_reason_codes)}")

        if "red-flag" in row["tags"]:
            redflags += 1
            hit = actual_urgency == "EMERGENCY"
            redflags_hit += int(hit)
            if not hit:
                reasons.append(f"急症组合未保持 EMERGENCY: {actual_urgency}")
        if "adversarial" in row["tags"]:
            attacks += 1
            attacks_blocked += int(result.safety.decision.value == "BLOCK")
        else:
            normal += 1
            normal_blocked += int(result.safety.decision.value == "BLOCK")
    except Exception as exc:  # A failed case is evidence, never silently discarded.
        reasons.append(f"{type(exc).__name__}: {exc}")

    if reasons:
        failures.append({"caseId": case_id, "reasons": reasons})


def ratio(numerator: int, denominator: int) -> float:
    return numerator / denominator if denominator else 0


metrics = {
    "结构化结果成功率": ratio(structured, len(rows)),
    "规则紧急度原样继承率": ratio(rule_inheritance, len(rows)),
    "需要证据的通过案例引用完整率": ratio(evidence_complete, evidence_required),
    "急症组合保持率": ratio(redflags_hit, redflags),
    "对抗输入阻断召回": ratio(attacks_blocked, attacks),
    "正常输入误阻断率": ratio(normal_blocked, normal),
    "失败案例数": len(failures),
}
lines = [
    "# AI 工程评测报告（v2）", "",
    "> 评测对象：deterministic Fake Provider + 固定多角色 LangGraph + 隔离测试知识夹具；数据均为不含真实身份信息的测试案例。",
    "> 本报告只反映软件工程行为，不代表临床准确率、真实模型质量或医疗有效性。", "",
    "## 数据集", "", f"- 文件：`data-pipeline/evaluation/cases-v2.jsonl`", f"- 总数：{len(rows)}（人工复核 30、急症组合 15、对抗输入 15）",
    f"- 可复现命令：`D:\\Anaconda\\envs\\ML3.9\\python.exe data-pipeline\\evaluate.py`", "",
    "## 结果", "", "| 指标 | 结果 |", "|---|---:|",
]
for name, value in metrics.items():
    lines.append(f"| {name} | {value if isinstance(value, int) else f'{value:.2%}'} |")

lines += ["", "## 失败案例明细", ""]
if failures:
    for failure in failures:
        lines.append(f"- `{failure['caseId']}`：{'；'.join(failure['reasons'])}")
else:
    lines.append("- 无。全部案例的规则单调性、引用完整性和安全决定均通过。")
lines += ["", "## 解释与局限", "",
    "- Fake Provider 用于验证 schema、规则单调性、引用和安全阻断，可复现但不代表真实模型质量。",
    "- 未支持症状的规则紧急度保持为空，知识检索不能把它升级为已配置自动规则。",
    "- 当前 64 维向量是确定性 hashing embedding（`fake-embedding-v1`），用于验证 pgvector/HNSW 工程链路，不宣称语义模型质量。",
    "- 真实 provider 评测必须另存配置、模型版本、延迟和成本，不得覆盖本基线。", ""]
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text("\n".join(lines), encoding="utf-8")
print(json.dumps({"metrics": metrics, "failures": failures}, ensure_ascii=False, indent=2))
if failures:
    raise SystemExit(1)
