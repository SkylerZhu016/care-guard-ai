# 02 架构规划

## 1. 总体架构

```
浏览器(Vue3) ──► Nginx:80 ──/api──► Spring Boot:8080 ──JPA──► PostgreSQL+pgvector:5432
                    │                   │  ▲                        ▲
                    └──静态资源          │  │内部REST(X-Internal-Token)│ SQLAlchemy
                                        ▼  │                        │
                              FastAPI:8000 ─┘ (直写AI产物表+完成回调)  │
                                        │                          │
                                        └──► MinIO:9000 ◄──────────┘
```

职责切分（硬约束）：
- **Spring Boot**：业务状态、权限、持久化、规则引擎、审计、SSE、MinIO 文件。唯一写业务状态（visits/followup/users…）的服务。
- **FastAPI**：大模型调用、RAG、文本结构化、安全审查。只写 AI 产物表（agent_runs/steps、symptom_extractions、triage_results、citations、knowledge_chunks），业务状态变更通过 `/api/internal/**` 回调后端完成。

## 2. 关键链路时序

### 2.1 提交预问诊
```
患者→POST /visits→后端:建visit(SUBMITTED)+agent_run(PENDING)+审计
后端:状态机→STRUCTURING→POST ai/internal/pipeline/start(异步立即返回202)
AI:STRUCTURE(写extraction)→RETRIEVE(写citations)→RISK→SAFETY→SUMMARY(逐步写steps)
AI:写triage_results→POST 后端/internal/agent-runs/{id}/completed
后端:校验run状态→visit→PENDING_REVIEW(REVIEW_FAILED则FAILED+告警)
前端:SSE /agent-runs/{id}/events 逐步渲染；失败自动降轮询
```

### 2.2 知识摄取
```
ADMIN→POST /guidelines(multipart)→后端:存MinIO+uploaded_files+knowledge_documents(PENDING)
→POST ai/internal/knowledge/ingest→AI:取文件→解析(pdf/txt/md)→分块→嵌入→写chunks
→POST 后端/internal/knowledge/documents/{id}/ingested→置ENABLED+知识库版本+1
```

### 2.3 审核→随访
```
医生POST /reviews(APPROVE)→visit REVIEWED→POST /followup-plans→start→生成N个任务
随访完成→followup_records；调度器每日扫逾期→DELAYED；ESCALATED→safety_alerts
```

## 3. 目录结构

```
期末大作业/
├── frontend/            # Vue3+TS+Vite
│   ├── src/{api,assets,components,directives,layouts,router,stores,types,utils,views}
│   └── index.html vite.config.ts package.json ...
├── backend/             # Spring Boot
│   └── src/main/java/cn/edu/medplatform/
│       ├── {config,security,common,audit}
│       └── modules/{auth,user,patient,visit,rule,knowledge,agent,review,followup,alert,file,admin}
│           └── 每模块 {controller,service,entity,repository,dto}
│   └── src/main/resources/{application.yml,db/migration}
├── ai-service/          # FastAPI
│   └── app/{routers,schemas,services,agents,rag,safety,models,prompts,clients,core}
│   └── tests/
├── data-pipeline/       # 种子与演示重置脚本
├── deploy/              # docker-compose.yml nginx.conf .env.example
├── tests/               # 端到端(playwright)与评测脚本
├── docs/                # 本目录全部文档
└── README.md
```

## 4. 后端内部分层（纪律）
`controller(薄)` → `service(业务+事务)` → `repository(JPA)`；entity 不出 controller，走 dto；跨模块只调 service；统一 `GlobalExceptionHandler` 输出错误体；`TraceIdFilter` 生成并透传 traceId；`AuditAspect` 注解式审计。

## 5. AI 服务内部结构
`routers(pipeline/knowledge/search/health)` → `services/orchestrator` → `agents/*(五段)` → `rag/{chunker,embedder,retriever}` + `safety/checker` + `clients/llm(openai兼容+mock)` + `models/(SQLAlchemy)` + `schemas/(pydantic)`。配置 `core/config.py` 全走环境变量。

## 6. 前端结构
- stores：auth(用户/权限/token刷新) + visit(当前问诊草稿) + dict(枚举字典)；
- api 层按资源分文件，统一 axios 实例（401 自动刷新重放、错误提示、防重 loading）；
- 路由守卫：未登录→/login；角色不符→/403；`v-permission` 控制按钮级；
- SSE 封装 `useSse(url)`，断线降级 3s 轮询。

## 7. 架构决策记录（ADR 摘要）
| 决策 | 选择 | 理由 |
|---|---|---|
| 数据库 | PostgreSQL+pgvector | 业务+向量一库，减少组件；向量不替代业务库 |
| 任务队列 | FastAPI 后台任务 | 课程规模足够；Redis/Celery 留作增强 |
| 规则引擎 | DB 配置 JSON DSL + Java 求值器 | 确定性、可热更新、免引入 Drools |
| LLM | OpenAI 兼容客户端 + Mock 降级 | 离线演示兜底；Key 只走环境变量 |
| 嵌入 | HASH256+关键词混合，可插拔真实嵌入 | 无 GPU/无外网可跑通 RAG |
| 文件 | MinIO | 任务书要求；后端统一收发 |
