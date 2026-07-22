# PowerShell 版演示数据重置
$ErrorActionPreference = "Stop"
Set-Location "$PSScriptRoot\.."

Write-Host "=== 重置演示数据 ===" -ForegroundColor Cyan

$composeFile = "deploy/docker-compose.yml"
$dbContainer = docker compose -f $composeFile ps -q db

# 1. 清空业务表
$truncateSql = @"
TRUNCATE audit_logs, safety_alerts, citations, agent_run_steps, agent_runs,
  followup_records, followup_tasks, followup_plans, review_records,
  triage_results, symptom_extractions, rule_hits, symptoms, visit_status_logs, visits,
  patient_histories, allergy_records, medication_records, simulated_patients,
  knowledge_chunks, knowledge_documents, uploaded_files, refresh_tokens,
  user_roles, users CASCADE;
"@
$truncateSql | docker exec -i $dbContainer psql -U med -d medplatform

# 2. 重新执行种子数据
Get-Content "backend/src/main/resources/db/migration/V2__seed.sql" | docker exec -i $dbContainer psql -U med -d medplatform

# 3. 重启服务
docker compose -f $composeFile restart backend ai-service

Write-Host "=== 演示数据已重置 ===" -ForegroundColor Green
Write-Host "演示账号：admin/Admin@123456  doctor1/Doctor@123456  follow1/Follow@123456  patient1/Patient@123456"
