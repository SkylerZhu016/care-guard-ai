#!/usr/bin/env bash
# 重置演示数据 —— 需在 deploy 目录或项目根目录执行
set -e
cd "$(dirname "$0")/.."

echo "=== 重置演示数据 ==="

# 1. 清空业务表（保留表结构）
docker exec -i $(docker compose -f deploy/docker-compose.yml ps -q db) psql -U med -d medplatform <<'SQL'
TRUNCATE audit_logs, safety_alerts, citations, agent_run_steps, agent_runs,
  followup_records, followup_tasks, followup_plans, review_records,
  triage_results, symptom_extractions, rule_hits, symptoms, visit_status_logs, visits,
  patient_histories, allergy_records, medication_records, simulated_patients,
  knowledge_chunks, knowledge_documents, uploaded_files, refresh_tokens,
  user_roles, users CASCADE;
SQL

# 2. 重新执行种子数据
docker exec -i $(docker compose -f deploy/docker-compose.yml ps -q db) psql -U med -d medplatform < backend/src/main/resources/db/migration/V2__seed.sql

# 3. 重启后端和 AI 服务（触发 embedding 补算）
docker compose -f deploy/docker-compose.yml restart backend ai-service

echo "=== 演示数据已重置 ==="
echo "演示账号：admin/Admin@123456  doctor1/Doctor@123456  follow1/Follow@123456  patient1/Patient@123456"
