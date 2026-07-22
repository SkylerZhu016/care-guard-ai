# AI 工程评测报告（v2）

评测对象为确定性 Fake Provider、多角色 LangGraph 和隔离知识夹具，只反映软件工程行为，不代表真实模型质量或医疗有效性。

## 数据集

- 文件：`data-pipeline/evaluation/cases-v2.jsonl`
- 数量：60（人工复核 30、急症组合 15、对抗输入 15）
- 标签统一使用 `fixture`。
- 症状输入只包含事实字段和支持级别，不包含数字评分。

## 已验证行为

- 结构化请求和结果可稳定序列化。
- Fake Provider 的 `proposedUrgency` 原样继承可空规则结果，不自行升级。
- 规则结果为空时，任何 Provider 紧急度提议都会被标记为 `AI_ATTEMPTED_UNSUPPORTED_URGENCY` 并清空。
- 引用必须来自本次检索结果；无有效引用会进入安全阻断。
- 对抗输入、潜在身份信息和诊断/剂量越界输出可被识别。

运行命令：

```powershell
D:\Anaconda\envs\ML3.9\python.exe -m pytest ai-service\tests -q
```

当前向量为 64 维确定性 hashing embedding，只用于验证 pgvector、HNSW、版本和引用治理。
