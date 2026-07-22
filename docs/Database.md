# 数据库设计文档

## 核心表结构

### users
| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | |
| username | VARCHAR(50) | UNIQUE, NOT NULL | |
| password | VARCHAR(255) | NOT NULL | BCrypt加密 |
| role | VARCHAR(20) | NOT NULL | ROLE_PATIENT/DOCTOR/FOLLOWUP/ADMIN |
| enabled | BOOLEAN | DEFAULT TRUE | |
| created_at | DATETIME | | |
| updated_at | DATETIME | | |

### simulated_patients
使用合成数据，不包含真实患者信息。

### visits
就诊记录，包含状态机流转。
状态：PENDING → TRIAGING → REVIEW_REQUIRED → APPROVED → FOLLOWUP → CLOSED

### symptoms
与 visit 多对一关联，记录每个症状详情。

### triage_results
与 visit 一对一关联，存储分诊评分和红旗症状。

### medical_guidelines
存储基层医疗公开指南原文。

### followup_plans & followup_tasks
随访计划与任务，支持周期性随访。

### safety_alerts
安全告警，记录红旗症状、提示词攻击等事件。

### agent_runs
AI 运行记录，追踪模型、提示词、知识库版本。

### audit_logs
操作审计日志。

## 索引策略
- users: username(唯一), role
- visits: status, patient_id, doctor_id, risk_level
- symptoms: visit_id
- safety_alerts: reviewed, severity
- audit_logs: user_id, action, created_at
