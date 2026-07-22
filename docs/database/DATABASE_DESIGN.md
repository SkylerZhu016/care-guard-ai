# 数据库设计

- PostgreSQL 16 是开发、测试和 Compose 的唯一关系数据库，不使用 H2 等替代实现。
- Flyway `V1__baseline.sql` 建立用户、就诊、分诊结果、AI 运行、引用、随访、告警和审计表。
- 唯一约束：用户名、`agent_runs.run_id`、`visits.idempotency_key`。
- 队列索引：`visits(status, submitted_at)`；任务索引：`followup_tasks(status, due_at)`；审计索引：`audit_logs(target_type, target_id, created_at)`。
- 原子边界：提交+规则结果、审核+最终等级、激活计划+任务快照、AI 结果+引用+告警。
- 历史记录只追加；用户停用代替删除；演示环境重置使用显式 profile。
