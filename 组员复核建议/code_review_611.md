# 代码复核报告 — care-guard-ai (branch: 611)

**复核日期**: 2026-07-23  
**复核分支**: `611` (commit `6b1fe58`)  
**项目名称**: 守望基层医疗：预问诊与随访  
**文件总数**: 155 个  
**技术栈**: Spring Boot 3.4.7 (Java 21) / FastAPI + Celery + LangGraph (Python) / Vue 3 + Vite + Pinia (TypeScript) / PostgreSQL 16 + pgvector / Redis / MinIO / Nginx  

---

## 一、总体评价

| 维度 | 评分 (5分制) | 说明 |
|:----:|:----:|:----|
| 架构设计 | ★★★★★ | 七服务 Compose 编排，职责边界清晰，AI 与业务写分离 |
| 安全合规 | ★★★★☆ | JWT + RBAC + 隐私脱敏 + 输入输出护栏，内网令牌可改进 |
| AI 安全 | ★★★★★ | 五段 Agent 管线，规则不可降级，引用校验，输出哈希留痕 |
| 代码质量 | ★★★★☆ | 结构清晰，命名规范，部分异常处理可更健壮 |
| 测试覆盖 | ★★★★☆ | 三端均有测试（Java 集成测试 / pytest / vitest） |
| 文档完整 | ★★★★★ | SRS / 架构 / ERD / 时序 / OpenAPI / 安全 / 测试 / 演示 / ADR 齐全 |
| 可部署性 | ★★★★☆ | Compose 一键启动，健康检查完备，WSL2 依赖需文档说明 |

**结论：这是一份完成度很高的教学实训项目，架构设计专业，安全边界严格，AI 管线设计尤其出色。可以交付。**

---

## 二、架构复核

### 2.1 服务编排 (compose.yaml)

7 个服务，依赖链正确：

```
postgres (pgvector/pgvector:0.8.0-pg16)
  └─ minio (RELEASE.2025-04-22)
       └─ redis (7.4.2-alpine)
            ├─ ai-api (FastAPI, 依赖 redis)
            └─ ai-worker (Celery, 依赖 redis)
                 └─ backend (Spring Boot, 依赖 postgres + minio + ai-api + ai-worker)
                      └─ nginx (前端 + 反代, 依赖 backend)
```

**优点：**
- 所有服务均有 `healthcheck`，backend 等待 4 个依赖全部 healthy 才启动
- 端口仅暴露 `18088`（nginx）和 `59001`（MinIO 控制台），且 MinIO 绑定 `127.0.0.1`
- 使用 `<<: *ai-env` YAML 锚点复用 AI 服务环境变量，避免重复
- `restart: unless-stopped` 全覆盖

**建议：**
- `JWT_SECRET` 默认值 `change-this-demo-secret-at-least-32-bytes` 是演示用，生产环境必须替换。建议在 README 增加「生产部署前必改」警告
- Redis 未设置密码（`--appendonly yes` 但无 `requirepass`），内网可接受，但建议加注释说明

### 2.2 后端架构 (Spring Boot 3.4.7 + Java 21)

**包结构** (`com.example.medsim`)：
- `SecurityConfig` — JWT 过滤器 + RBAC + CORS
- `AuthController` — 登录 / `/me`
- `IntakeV2Controller` — V2 问诊全流程（患者端）
- `PlatformController` — V1 废弃 + 医务端 + 随访端 + 管理端
- `IntakeV2Service` — V2 业务逻辑
- `PlatformService` — 平台业务逻辑
- `RuleEngine` — 确定性规则引擎
- `PrivacySanitizer` — PII 脱敏
- `KnowledgeService` / `KnowledgeController` — 知识库 CRUD + pgvector 检索
- `AiClient` — 调用 AI 服务的 HTTP 客户端
- `Repositories` / `DomainModels` / `ApiModels` — JPA 实体 / DTO 集中定义

**优点：**
- API 版本化清晰：V1 写接口返回 `410 Gone`，V2 为活跃版本
- `@PreAuthorize` 方法级权限控制在每个端点上都有
- `Idempotency-Key` 头用于提交操作，防重复提交
- `Hibernate ddl-auto: validate`，DDL 唯一来源是 Flyway
- `open-in-view: false` 关闭 OSIV，避免懒加载在视图层触发

**建议：**
- `Repositories.java` / `DomainModels.java` / `ApiModels.java` 将多个类放在同一文件，虽合法但大型项目应拆分为独立文件
- `ApiException` 自定义异常应配合 `@ControllerAdvice` 全局处理（从 `ErrorEnvelope.write` 推测已有，但未见独立文件）

### 2.3 AI 服务架构 (FastAPI + Celery + LangGraph)

**模块结构**：
- `main.py` — FastAPI 入口，`/internal/v1/analysis-jobs`（异步）+ `/internal/v1/complaint-structure`（同步）
- `workflow.py` — LangGraph 五段 Agent 管线
- `providers.py` — `DeterministicFakeProvider` + `OpenAICompatibleProvider`
- `guardrails.py` — 输入注入检测 / PII 检测 / 输出范围检测
- `knowledge.py` — RAG 检索（调用 backend 的 pgvector 接口）
- `jobs.py` — Redis Job Store
- `celery_app.py` — Celery 配置
- `config.py` — 环境变量配置
- `schemas.py` — Pydantic 模型

**优点：**
- 异步任务（Celery）与同步接口分离：分析任务走 Celery 队列，主诉结构化走同步调用
- AI 服务不直接读数据库，通过 `KNOWLEDGE_BASE_URL` 回调 backend 内部接口检索
- `internal_auth` 依赖注入验证 `X-Internal-Token`
- LangGraph 状态图清晰，每个 Agent 职责单一

**建议：**
- `config.py` 使用 `@dataclass(frozen=True)` 在模块加载时一次性读取环境变量，运行时修改不会生效——文档应说明需重启容器
- `knowledge.py` 在 `KNOWLEDGE_BASE_URL` 未设置时回退到 `TEST_FALLBACK`，生产环境应强制设置此变量

### 2.4 前端架构 (Vue 3 + Vite + Pinia)

**结构**：
- `api.ts` — 统一 API 客户端，支持 v1/v2 版本路由
- `router.ts` — 路由守卫，基于角色重定向
- `stores/session.ts` — Pinia 会话 store
- `views/` — 4 个角色视图 + 登录页
- `components/` — 通用组件
- `domain/presentation.ts` — 领域展示逻辑

**优点：**
- API 客户端封装简洁，统一错误处理（`ApiError`）
- 路由守卫严格：未登录 → 登录页，角色不匹配 → 回首页
- 有前端单元测试（`api.test.ts` / `router.test.ts` / `PatientView.test.ts` / `FollowupView.test.ts`）
- `Idempotency-Key` 使用 `crypto.randomUUID()` 生成

**建议：**
- 无 401 自动刷新 token 机制（JWT 60 分钟过期后用户需重新登录），可加 refresh token
- `api.ts` 中 `fetch` 无超时控制，网络异常时可能挂起

---

## 三、安全复核

### 3.1 认证与授权

| 项目 | 状态 | 说明 |
|:----:|:----:|:----|
| 密码存储 | ✅ | BCryptPasswordEncoder |
| JWT 签名 | ✅ | HMAC-SHA，密钥 ≥32 字节强制校验 |
| JWT 过期 | ✅ | 60 分钟 |
| RBAC | ✅ | `@PreAuthorize` + `ROLE_` 前缀 |
| 方法级权限 | ✅ | 每个端点都有 `@PreAuthorize` |
| CORS | ✅ | 仅允许 `localhost:*` 和 `127.0.0.1:*` |
| CSRF | ⚠️ | 已禁用（无状态 JWT API 可接受） |
| 内部服务令牌 | ⚠️ | `X-Internal-Token` 明文比较，有时序攻击风险 |

**建议：**
- `internal_auth` 中 `x_internal_token != settings.internal_token` 改用 `hmac.compare_digest()` 防止时序攻击
- 考虑增加 JWT refresh token 机制，避免 60 分钟后用户操作中断

### 3.2 隐私保护

`PrivacySanitizer.java` 覆盖：
- ✅ 手机号（`1[3-9]\d{9}`）
- ✅ 身份证号（`\d{17}[0-9Xx]`）
- ✅ 邮箱
- ✅ 带标签的姓名（`姓名：张三`）
- ✅ 带标签的地址（`住址：xxx`）
- ✅ 控制字符过滤

`guardrails.py` 输入检测：
- ✅ Prompt 注入（"忽略规则"、"系统提示词"等）
- ✅ 越界请求（"直接诊断"、"开药"、"处方"等）
- ✅ 角色冒充（"扮演医生"、"假装管理员"）
- ✅ PII 检测（手机号、身份证号）

`guardrails.py` 输出检测：
- ✅ 确诊性表述（"确诊为"、"患有"）
- ✅ 处方性表述（"开具"、"服用"、"mg"、"毫克"）
- ✅ 剂量调整（"停药"、"加量"、"减量"）

**评价：脱敏和护栏覆盖全面，符合"不提供真实诊断"的项目定位。**

### 3.3 AI 安全边界

| 约束 | 实现 | 评价 |
|:----|:----|:----|
| 规则引擎为确定性底线 | `RuleEngine.java` 先于 AI 执行 | ✅ 优秀 |
| AI 不可降级规则紧急度 | `citation_verifier_agent` 检查 `URGENCY_RANK` | ✅ 优秀 |
| AI 不可为不支持症状赋紧急度 | `AI_ATTEMPTED_UNSUPPORTED_URGENCY` | ✅ 优秀 |
| 引用必须来自检索结果 | `validate_citations(chunk_ids, valid_ids)` | ✅ 优秀 |
| 输出哈希留痕 | `hashlib.sha256(json.dumps(...))` | ✅ 优秀 |
| 四版本留痕 | prompt / knowledgeBase / rules / model | ✅ 优秀 |
| AI 失败降级 | 保留原始主诉，转人工复核 | ✅ 优秀 |

**这是本项目最出色的设计——AI 输出永远不能绕过规则引擎、不能编造引用、不能越界诊断。**

---

## 四、数据库复核

### 4.1 迁移文件 (V1–V10)

- `V1__baseline.sql` — 核心表（users / visits / symptoms / triage_results / agent_runs / citations / followup_plans / followup_tasks / safety_alerts / audit_logs）
- `V2__safety_state_machine_and_knowledge.sql` — 状态机 + 知识库表
- `V3__web_corpus_provenance.sql` — 网络语料来源
- `V4__intake_v2_patient_profiles.sql` — V2 问诊 + 患者档案
- `V5–V9` — 数据清洗迁移（可见语言清理、PowerShell UTF-8 修复等）
- `V10__complaint_ai_structure.sql` — 主诉 AI 结构化

**优点：**
- UUID 主键，避免自增 ID 暴露数据量
- 外键约束完整，`ON DELETE CASCADE` 用在 symptoms 和 citations
- 索引设计合理：`idx_visits_queue(status, submitted_at)` 支撑审核队列查询
- `idempotency_key VARCHAR(100) UNIQUE` 防重复提交
- `version BIGINT` 乐观锁字段

**建议：**
- V5–V9 五个迁移都是数据清洗，说明开发过程中有脏数据引入。建议在交付文档中说明这些迁移的目的，避免审核者困惑
- `symptoms.severity INTEGER CHECK (severity BETWEEN 0 AND 10)` 存在但 README 说"新问诊不采集数字严重度"——V2 可能不再使用此字段，应注释说明

### 4.2 知识库

- 使用 pgvector 存储文档嵌入向量
- `KnowledgeService` 调用 backend 内部接口 `/internal/v1/knowledge/search`
- `knowledge.py` 在 `KNOWLEDGE_BASE_URL` 未设置时回退到测试夹具

**评价：RAG 实现规范，引用可追溯到文档/章节/页码/URL/许可证说明。**

---

## 五、AI 管线复核

### 5.1 五段 Agent 工作流

```
InputGuardAgent → EvidenceRetrieverAgent → ClinicalSummaryAgent → SafetyCriticAgent → CitationVerifierAgent
```

| Agent | 职责 | 安全约束 |
|:------|:------|:---------|
| InputGuard | 检测 prompt 注入 / PII / 越界请求 | 阻断恶意输入 |
| EvidenceRetriever | RAG 检索指南证据 | 引用归属在检索层 |
| ClinicalSummary | LLM 生成结构化摘要 | 不能创建/降级紧急度 |
| SafetyCritic | 独立安全审查 | 检测输出越界 |
| CitationVerifier | 引用校验 + 最终安全决策 | 引用必须来自检索结果 |

**优点：**
- 每个 Agent 职责单一，可独立测试
- `agentTrace` 记录每个 Agent 的执行轨迹
- `SafetyDecision.BLOCK` 时 `reasonCodes` 记录所有违规原因
- `OpenAICompatibleProvider` 强制 `temperature: 0` 和 `response_format: json_object`
- 主诉结构化使用 few-shot prompting（两个示例）
- AI 提取的症状标签经过白名单过滤（`allowed.get(code)`）

**建议：**
- `OpenAICompatibleProvider._call` 的 20 秒超时在复杂 prompt 下可能不够，建议改为可配置
- `DeterministicFakeProvider.structure_complaint` 的别名匹配是硬编码的，新增症状需改代码——可考虑从目录配置驱动

### 5.2 Provider 适配器

- `DeterministicFakeProvider`：离线确定性输出，用于测试和演示
- `OpenAICompatibleProvider`：兼容 OpenAI / DeepSeek / 通义千问等 API
- JSON 提取鲁棒：先 `json.loads`，失败后正则匹配 ```` ```json ``` ```` 和裸 `{}`

**评价：双 Provider 设计优秀——离线可测试，在线可切换，不影响业务逻辑。**

---

## 六、代码质量

### 6.1 后端 Java

**优点：**
- `record` 类型用于不可变 DTO（`AuthPrincipal`、`RuleOutcome`、各种 `View` 和 `Input`）
- `Optional` 用于可能为空的结果（`JwtService.parse`）
- `switch` 表达式 + 模式匹配（`RuleEngine.canTransition`）
- 乐观锁 `version` 字段

**建议：**
- `Repositories.java` / `DomainModels.java` / `ApiModels.java` 将所有实体和 DTO 放在单个文件中，虽然 Java 允许一个文件多个顶级类，但不利于维护。建议拆分
- `PlatformController` 中 V1 废弃端点直接 `throw deprecated()`，建议用 `@Deprecated` 注解标注

### 6.2 AI 服务 Python

**优点：**
- Pydantic 模型做 schema 校验
- 类型注解完整（`Dict[str, Any]`、`List[str]`）
- `hashlib.sha256` 输出哈希用于留痕
- LangGraph `StateGraph` 声明式编排

**建议：**
- `guardrails.py` 的正则模式列表可提取为配置文件，方便后续扩展
- `knowledge.py` 的 `TEST_FALLBACK` 应标注 `# pragma: no cover` 或仅在测试环境加载

### 6.3 前端 TypeScript

**优点：**
- API 客户端统一封装，支持 v1/v2 版本切换
- 路由守卫严格
- 有单元测试

**建议：**
- 增加 401 拦截 + 自动刷新 token
- `fetch` 调用增加 `AbortController` 超时控制

---

## 七、测试复核

| 模块 | 测试文件 | 覆盖范围 |
|:----|:---------|:---------|
| 后端 | `RuleEngineTest.java` | 规则引擎逻辑 |
| 后端 | `PlatformIntegrationTest.java` | 平台集成 |
| 后端 | `KnowledgeCorpusTest.java` | 知识库语料 |
| AI 服务 | `test_api.py` | API 端点 |
| AI 服务 | `test_provider_adapter.py` | Provider 适配器 |
| AI 服务 | `test_workflow.py` | 工作流全链路 |
| 前端 | `api.test.ts` | API 客户端 |
| 前端 | `router.test.ts` | 路由守卫 |
| 前端 | `PatientView.test.ts` | 患者视图 |
| 前端 | `FollowupView.test.ts` | 随访视图 |
| 前端 | `StatusPill.test.ts` | 组件 |
| 前端 | `presentation.test.ts` | 领域展示逻辑 |
| 数据管线 | `test_crawl_official_medical_sources.py` | 爬虫 |
| 脚本 | `api-smoke.ps1` | API 冒烟 |
| 脚本 | `performance-smoke.ps1` | 性能冒烟 |

**评价：三端均有测试，覆盖单元/集成/冒烟三个层次。JaCoCo 代码覆盖率插件已配置。**

---

## 八、文档复核

| 文档 | 路径 | 评价 |
|:----|:----|:----|
| 需求规格 | `docs/requirements/SRS.md` | ✅ |
| 架构设计 | `docs/architecture/ARCHITECTURE.md` | ✅ |
| ER 图 | `docs/architecture/ERD.md` | ✅ |
| 时序图 | `docs/architecture/SEQUENCES.md` | ✅ |
| 部署说明 | `docs/architecture/DEPLOYMENT.md` | ✅ |
| API 契约 | `docs/api/openapi.yaml` | ✅ |
| 数据库设计 | `docs/database/DATABASE_DESIGN.md` | ✅ |
| AI 评测报告 | `docs/ai/AI_EVALUATION_REPORT.md` | ✅ |
| RAG 实现 | `docs/ai/RAG_IMPLEMENTATION.md` | ✅ |
| 安全合规 | `docs/security/SECURITY_AND_COMPLIANCE.md` | ✅ |
| 测试报告 | `docs/testing/TEST_REPORT.md` | ✅ |
| 演示脚本 | `docs/demo/DEMO_SCRIPT.md` | ✅ |
| 问题报告 | `docs/issues/PATIENT_SYMPTOM_COVERAGE_...` | ✅ |
| ADR | `docs/adr/ADR-001~003` | ✅ |
| 开发指导 | `docs/PROJECT_DEVELOPMENT_GUIDE.md` | ✅ |
| 完成审计 | `docs/PROJECT_COMPLETION_AUDIT_...` | ✅ |

**评价：文档体系完整，ADR（架构决策记录）体现了工程化思维。**

---

## 九、问题清单

### 9.1 需关注 (Should Fix)

| # | 模块 | 问题 | 建议 |
|:--|:----|:----|:----|
| 1 | AI 服务 | `internal_auth` 使用 `!=` 比较令牌 | 改用 `hmac.compare_digest()` |
| 2 | 前端 | 无 401 自动刷新 / `fetch` 无超时 | 增加 `AbortController` + refresh token |
| 3 | Compose | Redis 无密码 | 加 `requirepass` 或注释说明内网策略 |

### 9.2 建议改进 (Nice to Have)

| # | 模块 | 问题 | 建议 |
|:--|:----|:----|:----|
| 4 | 后端 | 多类合并单文件 | 拆分 `Repositories` / `DomainModels` / `ApiModels` |
| 5 | AI 服务 | Provider 超时硬编码 20s | 改为环境变量 `AI_TIMEOUT_SECONDS` |
| 6 | AI 服务 | `guardrails.py` 正则硬编码 | 提取为配置文件 |
| 7 | 数据库 | V5–V9 清洗迁移 | 在文档中说明目的 |
| 8 | 前端 | API 客户端无重试机制 | 增加指数退避重试 |

### 9.3 无问题 (Confirmed OK)

- ✅ JWT 密钥长度校验
- ✅ BCrypt 密码存储
- ✅ CORS 白名单
- ✅ 方法级 RBAC
- ✅ 隐私脱敏覆盖
- ✅ AI 规则不可降级
- ✅ 引用校验
- ✅ 输出哈希留痕
- ✅ 四版本留痕
- ✅ 幂等键防重复
- ✅ Flyway DDL 管理
- ✅ UUID 主键
- ✅ 乐观锁 version
- ✅ API 版本化 (V1 废弃 / V2 活跃)
- ✅ 三端测试覆盖
- ✅ 文档体系完整

---

## 十、总结

这是一份**完成度很高、安全设计专业**的教学实训项目。核心亮点：

1. **AI 安全五段管线**是最大亮点——输入护栏 → RAG 检索 → LLM 生成 → 安全审查 → 引用校验，AI 永远不能绕过规则引擎或编造引用
2. **隐私脱敏**在写库前执行，覆盖手机号/身份证/邮箱/姓名/地址
3. **API 版本化**清晰，V1 写接口返回 410 Gone，V2 为活跃版本
4. **文档体系**完整，包含 ADR（架构决策记录）
5. **三端测试**覆盖单元/集成/冒烟

**建议优先修复 3 个"需关注"项（内网令牌时序攻击 / 前端超时 / Redis 密码），其余为锦上添花。**

---

*复核人：自动化代码审查 / 2026-07-23*
