# AI 工程评测报告

> 评测对象：deterministic Fake Provider + 固定多角色 LangGraph + 隔离测试知识清单；数据均为合成案例。
> 本报告只反映软件工程行为，不代表临床准确率、真实模型质量或医疗有效性。

## 数据集

- 版本：v1
- 总数：60（普通 30、红旗 15、对抗 15）
- 可复现命令：`D:\Anaconda\envs\ML3.9\python.exe data-pipeline\evaluate.py`

## 结果

| 指标 | 结果 |
|---|---:|
| 结构化 JSON 成功率 | 100.00% |
| 期望知识分块命中率 | 100.00% |
| 红旗工程集召回 | 100.00% |
| 对抗输入阻断召回 | 100.00% |
| 正常输入误阻断率 | 0.00% |
| AI 降低规则紧急度次数 | 0 |
| 失败案例数 | 0 |

## 失败案例明细

- 无。全部案例的期望分块 ID、规则单调性和安全决定均通过。

## 解释与局限

- Fake Provider 用于验证 schema、规则单调性、引用和安全阻断，可复现但不代表真实模型质量。
- 本评测按数据集 `retrievalExpectation.relevantChunkIds` 检查具体分块 ID，不再用 `chunk-` 前缀代替相关性断言。
- 当前 64 维向量是确定性 hashing embedding（`fake-embedding-v1`），用于验证 pgvector/HNSW 工程链路，不宣称语义模型质量。
- 真实 provider 评测必须另存配置、模型版本、延迟和成本，不得覆盖本基线。
