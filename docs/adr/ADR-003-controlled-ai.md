# ADR-003：受控 Fake Provider 与安全门

- 状态：已采纳
- 日期：2026-07-22
- 决策：默认启用确定性 Fake Provider；固定 InputGuard → Retriever → RiskReviewer → SafetyReviewer → CitationVerifier → OutputBuilder 流程。
- 原因：无密钥也可重复测试成功、阻断和超时路径；真实 OpenAI-compatible provider 后续只替换 provider 适配器。
- 后果：界面和日志必须明确标记 Fake，不能冒充真实模型效果。

