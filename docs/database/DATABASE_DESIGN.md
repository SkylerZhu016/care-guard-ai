# 数据库设计

- PostgreSQL 16 是开发、测试和 Compose 的唯一业务数据库，不使用 H2。
- Flyway V1–V3 是已发布历史迁移，不修改；V4 完成患者角色和 v2 问诊迁移。
- `visits.intake_version` 区分 `INTAKE_V1` 与 `INTAKE_V2`，`profile_snapshot` 保存提交时资料。
- `symptoms.legacy_severity` 只服务历史只读；新问诊写入目录版本、支持级别、来源、事实字段和 JSONB 答案。
- `triage_results.rule_urgency` 可空；`coverage_status` 为 `FULL/PARTIAL/NONE`，`assessment_status` 为规则已评估或需要人工复核。
- `patient_profiles.owner_id` 唯一；`visit_supplements` 只追加。
- 唯一约束覆盖用户名、运行 ID、问诊幂等键和每问诊一个随访计划。
- pgvector 使用 HNSW cosine 索引；知识版本、来源 URL、哈希和对象键可审计。
