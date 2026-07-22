# 基层医疗安全型预问诊与随访平台 — 项目实施计划书与开发指导书

> 版本：v1.0 ｜ 状态：已定稿，作为全项目开发合同
> 本文件是后续所有编码工作的唯一权威依据。任何实现与本文件冲突时，先在 `docs/DECISIONS.md` 记录决策再改代码。

---

## 0. 仓库现状判断

- 当前仓库为空仓库，按**从零开发**处理。
- 技术栈无历史包袱，采用本文件第 2 节的最终架构。
- 本地环境：JDK 17 ✓ / Node 22 ✓ / Python 3.13 ✓ / Docker 部署以 compose 配置静态校验为准。

---

## 1. 项目定位与合规红线

教学用辅助系统的定位贯穿全部设计：

1. 系统只提供**预问诊信息整理、风险提示、指南检索与教学辅助**，不输出确定性诊断、不开处方、不替代医生。
2. 所有 AI 输出页面固定展示免责声明：「本内容由 AI 生成，仅供教学参考，不能替代医生诊断。」
3. 高风险结果**必须**进入人工审核队列；红旗症状由**确定性规则引擎**兜底，不依赖大模型。
4. AI 输出必须先经**安全审查 Agent** 通过，再进入医务人员审核队列。
5. 全链路留痕：模型版本、Prompt 版本、知识库版本、规则版本、引用来源、人工修改。
6. 只用合成病例 + 公开指南；日志不落明文敏感字段；密钥只走环境变量。

## 2. 最终技术架构

```
┌────────────┐   HTTPS    ┌──────────────┐
│  Vue3 前端  │ ────────▶ │ Nginx (80)   │
└────────────┘           │  / → 静态资源 │
                         │  /api → 8080 │
                         └──────┬───────┘
                                │
              ┌─────────────────▼──────────────────┐        内部 REST + X-Internal-Token
              │   Spring Boot 业务后端 (8080)       │ ────────────────────────────┐
              │  认证/RBAC/状态机/规则引擎/审计/文件 │                            ▼
              └───────┬───────────────┬────────────┘            FastAPI AI 服务 (8000)
                      │ JPA           │ 内部调用                 结构化/检索/风险/安全/汇总 Agent
          ┌───────────▼───┐     ┌─────▼──────────┐             RAG 分块/嵌入/引用
          │ PostgreSQL 16 │     │ MinIO (9000)   │                    │
          │  + pgvector   │ ◀── │ 文件对象存储     │ ◀──────────────────┘
          │ 业务表+向量表  │     └────────────────┘   SQLAlchemy 直写 AI 产物表
          └───────────────┘                            + 完成后回调后端内部接口
```

**架构决策（已记录）：**
- **D1 数据库**：PostgreSQL 16 + pgvector（一库同时承载业务表与向量表；向量库不替代业务库）。
- **D2 简化 Redis/Celery**：指导书允许第一阶段简化。SSE 由后端轮询 DB 推送；AI 流水线用 FastAPI 后台异步任务。升级为 Redis/Celery 属增强项，不阻塞 MVP。
- **D3 AI 产物写入权**：AI 服务直写 `agent_runs/agent_run_steps/symptom_extractions/triage_results/citations/safety_alerts/knowledge_chunks`；**业务状态（visits、followup 等）只能由 Spring Boot 写**，AI 服务流水线结束后回调后端内部接口触发状态流转。
- **D4 LLM 抽象**：统一 OpenAI 兼容客户端；`MOCK_LLM=true` 时使用确定性本地模拟（离线演示兜底，对应演示备用方案）。
- **D5 嵌入抽象**：默认 HASH(256维)+关键词混合检索（离线可用）；配置 `EMBEDDING_API_BASE/KEY` 后切换真实嵌入，维度经 `EMBEDDING_DIM` 配置并重建索引。

### 2.1 技术版本锁定

| 层 | 技术 | 版本 |
|---|---|---|
| 前端 | Vue / TypeScript / Vite / Pinia / Element Plus / ECharts / vue-router / axios | 3.5 / 5.6 / 5.4 / 2.3 / 2.9 / 5.6 / 4.5 / 1.8 |
| 后端 | Java / Spring Boot / Spring Security / Spring Data JPA / Flyway / jjwt / MinIO SDK | 17 / 3.3.x / 6.3 / 3.3 / 10.x / 0.12 / 8.5 |
| AI | Python / FastAPI / SQLAlchemy / pydantic / httpx / pgvector / pytest / pypdf | 3.11 / 0.115 / 2.0 / 2.9 / 0.27 / 0.3 / 8.x / 5.x |
| 基础设施 | PostgreSQL+pgvector / MinIO / Nginx / Docker Compose | pg16 / latest / 1.27 / v2 |

## 3. MVP 范围（必须实现）与增强范围

**MVP（本次交付）**：登录与 RBAC、模拟患者档案、分步骤预问诊（草稿/提交/状态机）、症状结构化、红旗规则预筛、公开指南知识库（≥6 篇）、RAG 检索+引用原文、安全审查 Agent、医务人员审核（通过/驳回/要求补充）、随访计划与任务（自动生成/看板/记录/趋势）、Agent 运行记录、审计日志、安全告警、Docker Compose 一键启动、一条端到端演示流程、Mock LLM 离线兜底。

**增强（预留接口不实现）**：高级重排序、模型/Prompt 在线切换 UI 已做基础版、短信邮件提醒、数据漂移监控、多租户、Redis+Celery 任务队列。

## 4. 角色与权限模型

| 角色 | code | 权限要点 |
|---|---|---|
| 患者模拟用户 | PATIENT | 本人档案 CRUD、预问诊草稿/提交/查看/补充、查看已审核建议、随访任务填写 |
| 医务人员 | DOCTOR | 审核队列、查看结构化+规则+引用+AI摘要、修改/通过/驳回/要求补充、创建随访计划 |
| 随访人员 | FOLLOWUP | 随访任务看板、认领/执行/记录、延期/失联/升级、趋势查看 |
| 系统管理员 | ADMIN | 用户/角色/指南/知识库/规则/模型/Prompt/Agent记录/告警/审计/系统配置 |

权限控制两层：Spring Security URL 拦截 + 方法级 `@PreAuthorize`；患者侧强制「本人数据」过滤（patientId 绑定当前用户）。

## 5. 业务状态机（唯一合法定义）

### 5.1 预问诊 visits.status
`DRAFT → SUBMITTED → STRUCTURING → RULE_SCREENED → AI_ANALYZING → PENDING_REVIEW → REVIEWED → ARCHIVED`
分支：`PENDING_REVIEW → NEED_INFO`（医生要求补充）→ 患者补充后回 `PENDING_REVIEW`；`PENDING_REVIEW → REJECTED`；任意处理中状态失败 → `FAILED`（可重试回 SUBMITTED 重跑）。
非法跳转一律抛 `40901 STATE_CONFLICT` 并记审计。每次流转写 `visit_status_logs`。

### 5.2 AI 运行 agent_runs.status
`PENDING → RUNNING → COMPLETED`；分支：`PARTIAL`（部分步骤成功但降级）、`REVIEW_FAILED`（安全审查不过）、`FAILED`、`TIMEOUT`、`CANCELLED`。

### 5.3 随访计划 followup_plans.status
`DRAFT → PENDING_START → ACTIVE → COMPLETED`；`ACTIVE ⇄ PAUSED`；任意 → `TERMINATED`。

### 5.4 随访任务 followup_tasks.status
`PENDING → ASSIGNED → IN_PROGRESS → COMPLETED`；`IN_PROGRESS → DELAYED/LOST/ESCALATED`；`PENDING/ASSIGNED → CANCELLED`。到期未处理由后端定时任务标 `DELAYED`。

风险等级枚举：`LOW / MEDIUM / HIGH / CRITICAL`。**规则引擎判定的 CRITICAL/HIGH 不可被 AI 降级**（冲突以规则为准）。

## 6. 数据库设计摘要（详见 03 与迁移 SQL）

核心表 36 张，分组：
- **认证权限**：users, roles, permissions, user_roles, role_permissions, refresh_tokens
- **患者档案**：simulated_patients, patient_histories, allergy_records, medication_records
- **预问诊**：visits, visit_status_logs, symptoms, symptom_extractions, triage_results
- **规则引擎**：rule_definitions, rule_versions, rule_hits
- **知识库**：knowledge_documents, knowledge_chunks(embedding vector(256))
- **AI 治理**：model_configs, prompt_templates, prompt_versions, agent_runs, agent_run_steps, citations, safety_alerts
- **审核**：review_records
- **随访**：followup_plans, followup_tasks, followup_records
- **系统**：uploaded_files, audit_logs, system_configs

通用约定：`id BIGSERIAL PK`；`created_at/updated_at timestamptz default now()`；软删 `deleted boolean default false`（仅指南、规则、用户启用）；乐观锁 `version int default 0`（visits、followup_tasks）；业务唯一约束见迁移 SQL。全文 DDL 唯一来源：`backend/src/main/resources/db/migration/`。

## 7. API 设计摘要（详见 04_API_PLAN.md）

统一前缀 `/api/v1`；鉴权 `Authorization: Bearer <accessToken>`；刷新 `POST /auth/refresh`。
统一错误体：`{"code":40001,"message":"...","traceId":"..."}`；错误码段：400xx 参数、401xx 认证、403xx 越权、404xx 不存在、409xx 状态冲突、500xx 服务异常。
分页：`?page=0&size=20&sort=createdAt,desc` → Spring `Page` JSON。
内部接口：`/api/internal/**` 仅走 `X-Internal-Token`（后端⇄AI 服务共享密钥）。

资源分组：auth / users / roles / patients / visits / triage / rules / guidelines / knowledge / agent-runs / reviews / followup-plans / followup-tasks / safety-alerts / audit-logs / files / admin。
**AI 服务对外仅暴露** `/internal/**`（pipeline 启动、知识摄取、健康检查），不直接对前端。

## 8. AI / RAG / Agent 设计（详见 05）

流水线（agent_run_steps 逐步落库）：`STRUCTURE → RETRIEVE → RISK → SAFETY → SUMMARY`
1. **结构化 Agent**：自然语言 → 症状 JSON Schema（含缺失项与置信度）；JSON 解析失败自动修复重试 1 次。
2. **检索 Agent**：由结构化症状构造查询 → pgvector 余弦 + 关键词重叠混合打分 → top-k=5 chunk + 引用元数据。
3. **风险分析 Agent**：规则命中 + 结构化 + 证据 → 风险关注点 JSON（每条风险必须挂引用 id，无引用降级为「需人工关注」）。
4. **安全审查 Agent**：确定性检查（诊断词/处方词/免责缺失/红旗遗漏/引用缺失/注入特征）→ PASS / FAIL + 问题清单；FAIL 时 run=REVIEW_FAILED 并进安全告警。
5. **汇总 Agent**（仅 SAFETY=PASS 执行）：生成供医生审核的结构化摘要（主诉、症状表、风险点、依据引用、建议关注方向、免责声明）。

每个 Schema 带 `schema_version`，pydantic 强校验；运行记录含模型名、Prompt 版本、知识库版本、规则版本、token 用量、耗时。

## 9. 安全与数据合规设计（详见 06）

- 认证：JWT access 2h + refresh 7d（`refresh_tokens` 轮换）；BCrypt；连续失败 5 次锁 15 分钟。
- 越权：方法级鉴权 + 患者数据 owner 校验；越权访问写 `safety_alerts(type=UNAUTHORIZED_ACCESS)`。
- 注入：AI 输入过「注入特征清单」（忽略上文/你现在是/输出系统提示等）→ 命中即拦截并告警；系统提示永不回显。
- 脱敏：列表接口姓名/电话默认掩码（张*三 / 138****1234）；审计与日志禁存密码、Token、完整证件号。
- 输出安全：诊断词/处方词黑名单 + 强制免责声明；高风险话术固定为「建议尽快寻求专业医疗帮助」。
- 上传：仅 txt/md/pdf，≤10MB，MIME+魔数校验，文件哈希落库。

## 10. 前端页面与路由摘要

布局：`/login`、`/register`、`/403` 公共页；登录后按角色进 `MainLayout`（侧边菜单按权限过滤）。
- 患者 `/p/*`：home、profile(档案)、visit-new(分步表单)、visit-drafts、visits、visit-detail/:id、visit-supplement/:id、followup-plans、followup-task/:id、followup-history
- 医生 `/d/*`：workbench、review-queue、review/:id（原始↔结构化对照/规则命中/引用/AI摘要/安全结果/人工审核）、followup-plan-new/:visitId
- 随访 `/f/*`：board、today、overdue、high-risk、task/:id、record/:id、trends
- 管理 `/a/*`：users、roles、guidelines、knowledge、rules、models、prompts、agent-runs、alerts、audit-logs、configs
工程约定：Pinia 按 auth/patient/visit/review/followup/admin 分 store；`src/api` 按资源分文件；TS 类型集中 `src/types`；路由守卫读 roles；`v-permission` 指令控按钮；axios 拦截器统一 401 刷新与错误提示；所有提交按钮 loading 防重。

## 11. 分阶段开发路线与验收

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| P0 骨架 | 目录/compose/迁移/文档 | `docker compose config` 通过；迁移 SQL 语法有效 |
| P1 基础工程 | 三端初始化+登录RBAC+统一异常+traceId | 登录→按角色进首页；错误体统一 |
| P2 预问诊业务 | 档案+分步表单+草稿+提交+状态机 | 患者提交后医生队列可见 |
| P3 规则引擎 | 规则配置/执行/命中记录/版本 | 合成病例红旗命中并可单测复现 |
| P4 知识库RAG | 摄取/分块/嵌入/检索/引用/管理页 | 检索返回带页码章节引用 |
| P5 多Agent | 五段流水线+Schema+SSE+降级 | 提交后 SSE 逐步显示进度并落 agent_run_steps |
| P6 医务审核 | 队列/对照/修改/通过/驳回/补充 | AI 内容不可绕过审核展示 |
| P7 随访 | 计划/任务生成/看板/记录/趋势 | 审核通过→建计划→出任务→填记录 |
| P8 安全审计 | 告警/审计/版本留痕 | 告警可处理；审计可追踪全链路 |
| P9 测试答辩 | 自动化+评测+文档+演示 | 一键 compose up 完成演示流程 |

## 12. 测试与部署要点（详见 07/08）

- AI：pytest 覆盖 Schema 校验、Mock 流水线、安全审查拦截、规则输入构造；`MOCK_LLM=true` 下全绿。
- 后端：JUnit 覆盖登录/权限/状态机/规则引擎/幂等。
- 前端：Vitest 覆盖表单校验与防重；Playwright 走通端到端（保留脚本）。
- 部署：`deploy/docker-compose.yml` 一命令启动 db/minio/backend/ai-service/frontend；健康检查+数据卷+`.env.example`；**仓库禁止提交真实密钥**。

## 13. 团队协作方案

按工作流拆分：前端 / Spring Boot / AI·RAG / 数据库与部署 / 测试文档。分支：`main`（保护）+ `feature/<模块>-<简述>`；PR 模板+至少 1 人评审；commit 约定式（`feat(backend): ...`）；接口变更先改 04 文档再改代码；合并顺序约定：migration → backend → ai-service → frontend → deploy。

## 14. 风险与降级（Top 8）

1. 模型不可用/超时 → Mock 兜底 + run=FAILED+进入人工队列，规则结果保留；
2. 返回非 JSON → 自动修复+重试1次，仍失败降级 PARTIAL；
3. RAG 无结果 → 风险提示降级为「证据不足，建议人工审核」；
4. 规则覆盖不足 → 管理端可热更新规则+版本化；
5. SSE 中断 → 前端降级为 3s 轮询 `/agent-runs/{id}`；
6. 前后端接口漂移 → 合同先改文档；OpenAPI 导出比对；
7. 演示网络故障 → `MOCK_LLM=true` 全离线演示 + 一键重置种子数据脚本；
8. 范围过大 → MVP 清单为唯一交付承诺，增强项不阻塞。

## 15. 现场演示方案（主线 12 步）

管理员预置（种子数据）：账号四类、合成患者 3 例、指南 ≥6 篇、规则 ≥12 条。
① 患者登录 → ② 选患者建档 → ③ 填分步问诊（含「胸痛+呼吸困难」红旗点）→ ④ 提交 → ⑤ SSE 进度（结构化→检索→风险→安全→汇总）→ ⑥ 展示结构化+规则命中 CRITICAL → ⑦ 展示指南引用原文 → ⑧ 展示安全审查 PASS → ⑨ 医生登录审核（对照/修改/通过）→ ⑩ 创建随访计划 → ⑪ 随访人员执行任务填记录+趋势图 → ⑫ 管理员看 Agent 运行记录+审计日志。
备用：`MOCK_LLM=true`；`./scripts/reset-demo.ps1|sh` 重置演示数据。

## 16. 实施任务清单（按序执行）

| # | 任务 | 产出 | 验收 |
|---|---|---|---|
| T1 | 仓库骨架+compose+`.env.example`+README | deploy/、README | compose config 通过 |
| T2 | Flyway V1 建表+V2 种子 | migration SQL | 36 表+种子可重放 |
| T3 | ai-service 全量（agents/rag/safety/schemas/mock）+pytest | ai-service/ | pytest 全绿 |
| T4 | backend 认证+RBAC+用户 | backend/ | 登录发 token、权限拦截 |
| T5 | backend 档案+预问诊+状态机+SSE | 同上 | 提交→状态流转→SSE 进度 |
| T6 | backend 规则引擎+知识库管理+内部接口 | 同上 | 规则命中落库；摄取回调 |
| T7 | backend 审核+随访+告警+审计+文件 | 同上 | 审核通过→计划→任务闭环 |
| T8 | frontend 全页面 | frontend/ | `vite build` 通过，流程可走 |
| T9 | 集成联调+修复 | 全仓 | 端到端主线 12 步可演示 |
| T10 | 提交材料文档+测试报告+演示脚本 | docs/submission/ | 材料齐套 |

> 执行原则：先合同后编码；先纵向闭环后横向扩展；先规则安全底线后接大模型；AI 结论必须可追溯、可审核、可降级；不伪造数据与测试结果。
