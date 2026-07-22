"""Run the deterministic engineering evaluation through the production workflow."""
from pathlib import Path
import json
import sys
from typing import Dict, List


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "ai-service"))
from app.schemas import AnalysisRequest  # noqa: E402
from app.workflow import analyze  # noqa: E402


DATASET = ROOT / "data-pipeline" / "evaluation" / "cases-v1.jsonl"
REPORT = ROOT / "docs" / "ai" / "AI_EVALUATION_REPORT.md"


rows = [json.loads(line) for line in DATASET.read_text(encoding="utf-8").splitlines() if line.strip()]
structured = retrieval_passed = redflags = redflags_hit = attacks = attacks_blocked = normal = normal_blocked = downgrades = 0
rank = {"ROUTINE": 0, "URGENT": 1, "EMERGENCY": 2}
failures: List[Dict[str, object]] = []

for row in rows:
    case_id = row["caseId"]
    expected_urgency = row["ruleExpectation"]["minimumUrgency"]
    request = AnalysisRequest(
        runId=f"run-{case_id}", visitId=f"visit-{case_id}", **row["input"],
        ruleUrgency=expected_urgency, ruleReasonCodes=row["ruleExpectation"]["expectedReasonCodes"]
    )
    reasons: List[str] = []
    try:
        result = analyze(request)
        structured += 1
        actual_ids = {citation.chunkId for citation in result.citations}
        expected_ids = set(row["retrievalExpectation"]["relevantChunkIds"])
        missing_ids = sorted(expected_ids - actual_ids)
        if missing_ids:
            reasons.append(f"缺少期望知识分块: {missing_ids}; 实际: {sorted(actual_ids)}")
        else:
            retrieval_passed += 1

        if rank[result.proposedUrgency.value] < rank[expected_urgency]:
            downgrades += 1
            reasons.append(f"紧急度从规则 {expected_urgency} 降为 {result.proposedUrgency.value}")

        expected_safety = row["safetyExpectation"]["decision"]
        if result.safety.decision.value != expected_safety:
            reasons.append(f"安全决定期望 {expected_safety}，实际 {result.safety.decision.value}")
        expected_reason_codes = set(row["safetyExpectation"].get("reasonCodes", []))
        actual_reason_codes = set(result.safety.reasonCodes)
        if not expected_reason_codes.issubset(actual_reason_codes):
            reasons.append(f"安全原因缺少 {sorted(expected_reason_codes - actual_reason_codes)}")

        if "red-flag" in row["tags"]:
            redflags += 1
            hit = result.proposedUrgency.value == "EMERGENCY"
            redflags_hit += int(hit)
            if not hit:
                reasons.append(f"红旗案例未达到 EMERGENCY: {result.proposedUrgency.value}")
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
    "结构化 JSON 成功率": ratio(structured, len(rows)),
    "期望知识分块命中率": ratio(retrieval_passed, len(rows)),
    "红旗工程集召回": ratio(redflags_hit, redflags),
    "对抗输入阻断召回": ratio(attacks_blocked, attacks),
    "正常输入误阻断率": ratio(normal_blocked, normal),
    "AI 降低规则紧急度次数": downgrades,
    "失败案例数": len(failures),
}
lines = [
    "# AI 工程评测报告", "",
    "> 评测对象：deterministic Fake Provider + 固定多角色 LangGraph + 隔离测试知识清单；数据均为合成案例。",
    "> 本报告只反映软件工程行为，不代表临床准确率、真实模型质量或医疗有效性。", "",
    "## 数据集", "", f"- 版本：v1", f"- 总数：{len(rows)}（普通 30、红旗 15、对抗 15）",
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
    lines.append("- 无。全部案例的期望分块 ID、规则单调性和安全决定均通过。")
lines += ["", "## 解释与局限", "",
    "- Fake Provider 用于验证 schema、规则单调性、引用和安全阻断，可复现但不代表真实模型质量。",
    "- 本评测按数据集 `retrievalExpectation.relevantChunkIds` 检查具体分块 ID，不再用 `chunk-` 前缀代替相关性断言。",
    "- 当前 64 维向量是确定性 hashing embedding（`fake-embedding-v1`），用于验证 pgvector/HNSW 工程链路，不宣称语义模型质量。",
    "- 真实 provider 评测必须另存配置、模型版本、延迟和成本，不得覆盖本基线。", ""]
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text("\n".join(lines), encoding="utf-8")
print(json.dumps({"metrics": metrics, "failures": failures}, ensure_ascii=False, indent=2))
if failures:
    raise SystemExit(1)
