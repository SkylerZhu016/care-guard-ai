"""Run deterministic engineering evaluation through the production workflow."""
from pathlib import Path
import json
import sys


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "ai-service"))
from app.schemas import AnalysisRequest  # noqa: E402
from app.workflow import analyze  # noqa: E402


DATASET = ROOT / "data-pipeline" / "evaluation" / "cases-v1.jsonl"
REPORT = ROOT / "docs" / "ai" / "AI_EVALUATION_REPORT.md"


rows = [json.loads(line) for line in DATASET.read_text(encoding="utf-8").splitlines() if line.strip()]
structured = valid_citations = redflags = redflags_hit = attacks = attacks_blocked = normal = normal_blocked = downgrades = 0
rank = {"ROUTINE": 0, "URGENT": 1, "EMERGENCY": 2}
for row in rows:
    expected = row["ruleExpectation"]["minimumUrgency"]
    request = AnalysisRequest(
        runId=f"run-{row['caseId']}", visitId=f"visit-{row['caseId']}", **row["input"],
        ruleUrgency=expected, ruleReasonCodes=row["ruleExpectation"]["expectedReasonCodes"]
    )
    try:
        result = analyze(request); structured += 1
        valid_citations += int(bool(result.citations) and all(c.chunkId.startswith("chunk-") for c in result.citations))
        downgrades += int(rank[result.proposedUrgency.value] < rank[expected])
        if "red-flag" in row["tags"]:
            redflags += 1; redflags_hit += int(result.proposedUrgency.value == "EMERGENCY")
        if "adversarial" in row["tags"]:
            attacks += 1; attacks_blocked += int(result.safety.decision.value == "BLOCK")
        else:
            normal += 1; normal_blocked += int(result.safety.decision.value == "BLOCK")
    except Exception:
        pass


def ratio(n, d): return n / d if d else 0
metrics = {
    "结构化 JSON 成功率": ratio(structured, len(rows)), "有效引用 ID 比例": ratio(valid_citations, structured),
    "红旗工程集召回": ratio(redflags_hit, redflags), "对抗输入阻断召回": ratio(attacks_blocked, attacks),
    "正常输入误阻断率": ratio(normal_blocked, normal), "AI 降低规则紧急度次数": downgrades,
}
lines = [
    "# AI 工程评测报告", "", "> 评测对象：deterministic Fake Provider + 固定 LangGraph；数据均为合成案例。", "> 本报告只反映软件工程行为，不代表临床准确率或医疗有效性。", "",
    "## 数据集", "", f"- 版本：v1", f"- 总数：{len(rows)}（普通 30、红旗 15、对抗 15）", f"- 可复现命令：`D:\\Anaconda\\envs\\ML3.9\\python.exe data-pipeline\\evaluate.py`", "",
    "## 结果", "", "| 指标 | 结果 |", "|---|---:|",
]
for name, value in metrics.items(): lines.append(f"| {name} | {value if isinstance(value, int) else f'{value:.2%}'} |")
lines += ["", "## 解释与局限", "", "- Fake Provider 用于验证 schema、规则单调性、引用和安全降级，可复现但不代表真实模型质量。", "- 当前知识分块是教学种子材料；正式答辩前应由具备医学背景的指导人员复核来源和内容。", "- 真实 provider 评测必须另存配置、模型版本、延迟和成本，不得覆盖本基线。", ""]
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text("\n".join(lines), encoding="utf-8")
print(json.dumps(metrics, ensure_ascii=False, indent=2))

