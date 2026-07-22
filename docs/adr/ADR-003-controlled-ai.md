# ADR-003：受控 Fake Provider 与安全门

- 状态：已采纳
- 日期：2026-07-22
- 决策：默认启用确定性 Fake Provider；固定 InputGuardAgent → EvidenceRetrieverAgent → ClinicalSummaryAgent → SafetyCriticAgent → CitationVerifierAgent 五角色流程。真实 OpenAI-compatible provider 对摘要和独立安全复核分别发起模型调用。
- 原因：无密钥也可重复测试成功、阻断和超时路径；真实 OpenAI-compatible provider 后续只替换 provider 适配器。
- 后果：界面和日志必须明确标记 Fake，不能冒充真实模型效果；`agentTrace` 只记录可观察步骤与状态，不存储或展示隐藏推理过程。

