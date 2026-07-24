# 数据库设计

- PostgreSQL 16 是开发、测试和 Compose 的唯一业务数据库，不使用 H2。
- Flyway V1–V11 均按版本追加且不回改历史文件；V4 完成患者角色和 v2 问诊迁移，V10 增加主诉 AI 结构，V11 将旧自动化请求留下的连续问号乱码替换为明确的“历史记录编码异常”说明，不猜测无法恢复的原文。
- `visits.intake_version` 区分 `INTAKE_V1` 与 `INTAKE_V2`，`profile_snapshot` 保存提交时资料。
- `symptoms.legacy_severity` 只服务历史只读；新问诊写入目录版本、支持级别、来源、事实字段和 JSONB 答案。
- `visit_complaint_analyses` 独立保存原始主诉、AI 结构、患者确认结构、运行元数据和失败状态；AI 标签不会暗中改写 `symptoms`。
- `triage_results.ai_detail` 保存医务端结构化辅助对象，`ai_summary` 继续作为历史兼容摘要。
- `triage_results.rule_urgency` 可空；`coverage_status` 为 `FULL/PARTIAL/NONE`，`assessment_status` 为规则已评估或需要人工复核。
- `patient_profiles.owner_id` 唯一；`visit_supplements` 只追加。
- 唯一约束覆盖用户名、运行 ID、问诊幂等键和每问诊一个随访计划。
- pgvector 使用 HNSW cosine 索引；知识版本、来源 URL、哈希和对象键可审计。
