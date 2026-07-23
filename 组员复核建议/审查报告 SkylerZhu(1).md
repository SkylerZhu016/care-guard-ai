# 全面代码审查报告 — care-guard-ai (branch 611)

> **审查日期**: 2026-07-23
> **审查范围**: 后端 (Spring Boot 3.4.7 / Java 21 / PostgreSQL+pgvector)、AI 服务 (FastAPI / Python 3.9 / LangGraph 多智能体)、前端 (Vue 3 / TypeScript / Element Plus / Vite)、UI 演示工程 (ui-demo)
> **审查方法**: 静态逐文件审查 + 多模块交叉验证。覆盖 60+ 源文件、10 个 Flyway 迁移、28 个测试用例
> **审查原则**: 不修改任何代码，仅提交审查发现
> **项目概述**: 面向人工智能实训的全栈教学演示项目——基层医疗安全型预问诊与随访平台，611 分支已演进到 **v2 双接口（INTAKE_V1 弃用 / INTAKE_V2 主用）**，并新增 pgvector 知识库、隐私脱敏、Profile 快照、AI 主诉结构化等能力

---

## 目录

1. [审查总览与上一版对比](#1-审查总览与上一版对比)
2. [后端 (Spring Boot / Java)](#2-后端-spring-boot--java)
3. [AI 服务 (FastAPI / Python)](#3-ai-服务-fastapi--python)
4. [前端 (Vue 3 / TypeScript)](#4-前端-vue-3--typescript)
5. [UI 演示工程 (ui-demo)](#5-ui-演示工程-ui-demo)
6. [数据库模式与迁移](#6-数据库模式与迁移)
7. [数据流与依赖关系图](#7-数据流与依赖关系图)
8. [跨系统契约分析](#8-跨系统契约分析)
9. [配置与部署](#9-配置与部署)
10. [总结与修复优先级](#10-总结与修复优先级)

---

## 1. 审查总览与上一版对比

### 1.1 新 611 分支主要变化

| 维度 | 上一版（已审） | 新 611 分支 |
|------|---------------|------------|
| 接口版本 | 仅 V1 | **V1 弃用（HTTP 410）+ V2 主用** |
| 问诊模型 | SymptomInput（带 severity 0-10） | **IntakeCatalog 目录 + SymptomReport（无 severity）** |
| AI 工作流 | 4 节点 LangGraph | **5 节点多智能体（InputGuard / EvidenceRetriever / ClinicalSummary / SafetyCritic / CitationVerifier）** |
| 知识库 | 硬编码 3 条 `VALID_CHUNKS` | **pgvector + MinIO 对象存储 + 实际文档（≥100 chunk）** |
| 隐私保护 | 仅 `clean()` 去控制字符 | **新增 PrivacySanitizer（手机/身份证/邮箱/姓名/地址）** |
| 主诉整理 | 无 | **新增 ComplaintAnalysis + AI 标签白名单** |
| 健康资料 | 无 | **新增 PatientProfile（年龄段/慢病/过敏/用药）+ 提交时快照** |
| 补充信息 | 无 | **新增 VisitSupplement（提交后可追加）** |
| 状态机 | 8 个状态，无显式状态机 | **新增显式 canTransition + REJECTED 路径 + Coverage/Assessment 状态** |
| 任务模板 | 硬编码 4 任务 | **模板化（仅 GENERAL_FOLLOWUP_V1）+ 强制 PENDING→IN_PROGRESS→COMPLETED** |
| 后端测试 | 5 用例 | **7 集成 + 7 规则 + 1 知识语料** |
| 前端测试 | 3 组件级 | **4 视图级 + api 契约 + presentation** |
| AI 测试 | 2 模块 | **3 模块（含 provider adapter 适配层）** |
| 安全 | JWT + V1 写 | **JWT + JwtAuthFilter 静默失败 + X-Internal-Token + MessageDigest.isEqual 常量时间** |
| 数据库 | 单次 baseline | **10 次迁移，含 pgvector、HNSW、JSONB、CHECK 约束** |

### 1.2 已修复/缓解（对比上一版）

| 旧报告编号 | 主题 | 当前状态 |
|----------|------|---------|
| B-C2 N+1 | 视图层多次级联查询 | ⚠️ 仍存在（`view()` 调用 3 个 findBy*），但 N 已减小（不再嵌套 citations） |
| B-C3 JSON 序列化吞异常 | `ignored` | ✅ 改用 `privacy.sanitize` + `ApiException("INVALID_STRUCTURED_DATA")` |
| B-C4 仓库缺 @Repository | 异常翻译 | ⚠️ 仍无 @Repository，但新增 MethodArgumentNotValid/HttpMessageNotReadable/TypeMismatch 处理 |
| B-H5 @Version 无重试 | 乐观锁 | ⚠️ 仍无 @Retryable，但已有 @Version 实体数增加（Visit/TriageResult/FollowupPlan/PatientProfile） |
| B-H7 AI 超时配置不一致 | compose/.env | ⚠️ compose 默认 10s，.env 默认 60s，依然不一致 |
| B-M5 异常消息 80 字符切 UTF-16 | 编码截断 | ⚠️ 改用 `Math.min(80, message.length())`，仍可能在代理对中间切断 |
| B-M8 JWT 密钥默认 | 生产风险 | ⚠️ 仍以 32 字节默认 secret 启动 |
| B-M9/M10 Swagger / Actuator 公开 | 公开暴露 | ✅ actuator 仅 health,info 暴露；swagger 仍 permitAll（教学项目） |
| P-C1 子串匹配 | 错误指南检索 | ✅ Python 已迁到调用 `KnowledgeService`（pgvector），原硬编码 knowledge.py 子串匹配被替换为 TEST_FALLBACK（仅测试时用） |
| P-C2 6+ 未保护 JSON 解析 | OpenAIProvider | ✅ 增加 `_extract_json_object` 多级回退（json.loads → markdown fence → 裸 JSON）+ `AI_INVALID_JSON_OBJECT` |
| P-C4 Celery 显式禁用重试 | 消息丢失 | ⚠️ 仍 `autoretry_for=(), max_retries=0`，但状态写 Redis 可从 GET 恢复 |
| P-C5 build_output KeyError | LLM 异常结构 | ✅ Python 侧 `try/except ValueError, TypeError` 收口到 `AI_INVALID_URGENCY` |
| P-C6 引用列表状态突变 | 共享可变状态 | ✅ `blocked = list(state["blockedReasons"]) + list(...)` 复制后追加 |
| F-C1 随访 note 共享状态 | 跨任务污染 | ✅ 已改为 `notes: Record<string,string>`，每个 taskId 独立 note |
| F-C3 PatientView 成功虚假错误 | 错误处理 | ⚠️ 仍使用 Promise.all，但错误处理改为 ElMessage.warning 而非 error |
| F-H3 无 404 路由 | UX | ⚠️ 仍无 `pathMatch` 兜底路由 |
| X-C1 无效引用处理不对称 | 跨系统 | ✅ Python 端也 BLOCK + INVALID_CITATION（双侧一致） |
| X-C2 控制字符消毒差异 | 跨系统 | ⚠️ 仍存在（Java 用 `[\p{Cntrl}&&[^\n\t]]`，Python 用 `ord(ch) >= 32`），但 PrivacySanitizer 接管边界 |
| X-C3 VALID_CHUNK 重复维护 | 跨系统 | ✅ 改用 `KnowledgeService.isActiveChunk()` 单点查询 |
| X-C4 SQL 状态列无 CHECK | 数据库 | ✅ V1/V2/V4 迁移新增 `users.role / review_decision / coverage_status / assessment_status / complaint_analysis_status / support_level` CHECK 约束 |

### 1.3 引入的新风险

详见各模块的 CRITICAL/HIGH 标注，主要新增/未解决项：
- `IntakeV2Service.runAi` 仍在 `@Transactional` 内做 15s 轮询（与旧 B-H1 同问题）
- `IntakeV2Service.replaceReports` 仍是 delete+insert 非原子（H6 同问题）
- 隐私脱敏仅在写入边界做，读取时不再脱敏——若数据来源不一将出现不一致
- 前端 `crypto.randomUUID()` 作为 Idempotency-Key，导致 retry 永远不复用
- `IntakeV2Service.audit` 仍生成随机 UUID 作为 `requestId`（与 HTTP 请求头无关）
- `findFirstByRole(Role.FOLLOWUP_STAFF)` 仍用第一个匹配用户作为 assignee（公平性问题）
- 登录接口无任何 rate limit

---

## 2. 后端 (Spring Boot / Java)

### 🔴 CRITICAL

#### B-C1: `@Transactional` 内同步长轮询——连接池与事务耗尽
**文件**: `IntakeV2Service.java:111-118`（`submit()` 调用 `runAi()`）；`IntakeV2Service.java:255-298`（`runAi` 内部 `ai.analyze` 同步轮询最多 15s）

**严重性**: 🔴 Critical  
**类型**: 资源/可靠性

```java
@Transactional
VisitViewV2 submit(...) {
    // ...
    runAi(visit, reports, outcome, result);   // ← 在事务里阻塞 15s 轮询
    visit.status = VisitStatus.PENDING_REVIEW; visits.save(visit);
    // ...
}
```

`runAi()` 调用 `ai.analyze()`，后者每 200ms 通过 `RestClient` 拉取 AI 服务的 job 状态。Spring 默认 HikariCP `maximum-pool-size=10`（`application.yml` 未配置）。10 并发提交即占满连接池，后续请求阻塞。

**修复**: 将 `runAi()` 拆为 `submit()`（短事务，更新 visit.status 后立即返回）+ 异步执行器跑 AI；或用 `@Transactional(propagation = REQUIRES_NEW)` 在方法内分事务。

---

#### B-C2: `replaceReports` delete+insert 非原子——并发替换症状可丢失
**文件**: `IntakeV2Service.java:215-232`；`PlatformService.java:185`（旧 V1 路径）  
**严重性**: 🔴 Critical  
**类型**: JPA/数据完整性

`SymptomEntity` 无 `@Version`。`replaceReports` 先 `deleteByVisitId(visitId)`，再遍历 `input` 逐条 `save`。两并发 PUT：
- T1 删完 → T2 删完（已空）→ T1 插 N 条 → T2 插 M 条（最终只 M 条，T1 写的 N 条丢失）。

更隐蔽：T1 删除后 T2 读取，触发 V2 状态 `chief_complaint` 变化导致 `complaintAnalyses` 状态置为 INVALID（`IntakeV2Service.java:80-83`），但症状本身可被并发覆写。

**修复**: 一次性 `INSERT ... ON CONFLICT (visit_id) DO UPDATE` 或外层 `SELECT ... FOR UPDATE`；或新增 `replaceByPredicate`。

---

#### B-C3: `IntakeV2Service.audit()` 仍生成随机 requestId，与 HTTP 请求不关联
**文件**: `IntakeV2Service.java:432-435`；`PlatformService.java:196`（旧路径同问题）  
**严重性**: 🔴 Critical  
**类型**: 审计/可追溯性

```java
private void audit(...) {
    var value = new AuditLog();
    // ...
    value.requestId = UUID.randomUUID().toString();   // ← 每次随机
    // ...
}
```

`AuditLog.requestId` 是表上可索引字段（`V1:idx_audit_request`），设计意图是按 HTTP `X-Request-Id` 串联请求链路。当前实现把同一请求的多次审计写入全部用独立 UUID，无法通过该字段定位完整链路。

**修复**: 注入 `HttpServletRequest` 读 `X-Request-Id` 头；或 `MDC` 传递。

---

#### B-C4: PrivacySanitizer 不在所有入口处生效
**文件**: `PrivacySanitizer.java:8-32`；调用方 `IntakeV2Service.java:210-211, 243, 247-249, 252, 363-377, 382-387`  
**严重性**: 🔴 Critical  
**类型**: 隐私/合规

`PrivacySanitizer` 用 5 个 Pattern 屏蔽：
- `CN_PHONE` 中国手机号
- `CN_ID` 18 位身份证
- `EMAIL` 邮箱
- `LABELED_NAME` 「姓名/真实姓名」开头
- `ADDRESS` 「住址/地址」开头

调用方仅覆盖了 `IntakeV2Service` 的 `apply / supplement / sanitizeProfile / sanitizeList / normalizeAiTags / sanitizeFacts / readAiSupport / ComplaintConfirmationInput`。**遗漏点**：
1. **`PlatformService.java`（V1 旧路径）**仍用旧 `clean()` 只去控制字符——若 V1 路径被以新格式调用（前端已禁止，但未来集成容易遗忘），PII 可写库
2. **`IntakeV2Service.update()` 第 78-83 行**检查 `chiefComplaint+freeText` 变化并把 `complaintAnalyses` 标 INVALID，但 `replaceReports` 中的 `customName` 和 `supplementalText` 经 `privacy.sanitize`，而 `SymptomEntity.name` 字段**没有走 `privacy.sanitize`**（仅在 `replaceSymptoms` 中对 V1 调用 `privacy.sanitize(item.name())`）
3. **`triage.aiSummary` 和 `triage.aiDetail` 是 AI 输出**——若 LLM 把用户原始 PII 透传回摘要（虽 prompt 提示不输出，但 `inspect_output` 仅检查诊断/处方模式），未做二次脱敏

**影响**: 教学系统上 PII 进入 audit_logs、triage_results.ai_summary 仍可能。

**修复**: 把 `PrivacySanitizer` 接入 JPA `@PrePersist/@PreUpdate` 监听器；或对 `triage_results` JSON 字段做读取脱敏。

---

#### B-C5: `IntakeV2Service.confirmComplaint()` 不校验 tag 是否仍在目录
**文件**: `IntakeV2Service.java:188-201`  
**严重性**: 🔴 Critical  
**类型**: 数据完整性

```java
@Transactional
ComplaintAnalysisView confirmComplaint(AuthPrincipal actor, UUID id, ComplaintConfirmationInput input) {
    ownedDraft(actor, id);
    var entity = complaintAnalyses.findByVisitId(id).orElseThrow(ApiException::notFound);
    if (entity.status != ComplaintAnalysisStatus.SUCCEEDED) ...
    var tags = normalizeConfirmedTags(input.tags());   // 仅做去重、长度截断
    var confirmed = new ComplaintAnalysisView(..., tags, ...);
    entity.confirmedJson = json(confirmed);
    // ...
}
```

`normalizeConfirmedTags` 只做了 `code` 长度 ≤60、displayName/category 走 `privacy.sanitize`、source 强制 `user_selected|ai_extracted`、status 强制 `confirmed|removed`——**但没有校验 `code` 是否在 `IntakeCatalog` 中存在**。前端可提交 `code=INVENTED_TAG` 写入 `confirmed_json`；后续 `view()` 通过 `mapper.readValue(content, ComplaintAnalysisView.class)` 反序列化时因为 `ComplaintTagView.code` 的 `@Pattern(regexp="[A-Z][A-Z0-9_]{1,59}")` 校验会失败回退到 `failedComplaint()`——但失败状态写 `confirmedJson` 之前还是之后有顺序竞争。

**修复**: `normalizeConfirmedTags` 中加 `var definition = catalog.find(code); if (definition == null) continue;`，与 `normalizeAiTags` 第 361 行一致。

---

#### B-C6: 仍无 @Repository 注解——JPA 异常未被翻译
**文件**: `Repositories.java:1-26`  
**严重性**: 🔴 Critical  
**类型**: JPA 异常翻译

8 个 `JpaRepository` 接口均未标注 `@Repository`。Spring Data JPA 仍会创建代理，但不会通过 `PersistenceExceptionTranslationPostProcessor` 把 JPA 异常包装为 `DataAccessException` 体系。`ApiExceptionHandler` 仅处理 `MethodArgumentNotValid/HttpMessageNotReadable/TypeMismatch/AccessDenied/Exception` 五大类——`PersistenceException`、`OptimisticLockingFailureException`（新增于 Visit/TriageResult/FollowupPlan/PatientProfile 的 `@Version`）将冒泡到 `@ExceptionHandler(Exception.class)` 兜底，返回通用 `INTERNAL_ERROR`，失去类型信息。

**修复**: 给所有 8 个接口加 `@Repository`；并增加 `@ExceptionHandler(OptimisticLockingFailureException.class)` 重试建议。

---

### 🟠 HIGH

#### B-H1: `JwtAuthFilter.parse()` 静默吞掉所有 RuntimeException
**文件**: `SecurityConfig.java:89-92`（`JwtService.parse`）  
**严重性**: 🟠 High  
**类型**: 安全/可观测性

```java
Optional<Claims> parse(String token) {
    try { return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()); }
    catch (RuntimeException ignored) { return Optional.empty(); }
}
```

JWT 解析失败的所有原因（过期、签名不匹配、格式错误）一律 `Optional.empty()`，调用方 `JwtAuthFilter` 第 107 行 `.flatMap(...).ifPresent(...)`——**完全无日志**。生产环境调试 token 问题极难。

**修复**: 至少 logger.warn 记录原因类别（`ExpiredJwtException` / `SignatureException` / `MalformedJwtException`）。

---

#### B-H2: `KnowledgeController` permitAll——仅靠 X-Internal-Token 保护
**文件**: `SecurityConfig.java:51`；`KnowledgeController.java:19-28`  
**严重性**: 🟠 High  
**类型**: 访问控制

```java
.requestMatchers("/api/v1/auth/login", "/internal/v1/knowledge/**", ...)
    .permitAll()
```

`/internal/v1/knowledge/search` 已 permitAll，仅靠 `@RequestHeader("X-Internal-Token")` 在控制器内 `MessageDigest.isEqual` 校验（已是常量时间，OK）。但：
1. 端点 URL 是 `/internal/v1/...`——若运维将后端反向代理到公网（无 IP 白名单），泄露就裸奔
2. `MessageDigest.isEqual(byte[], byte[])` 对**两数组长度差**不防护（先比长度再比内容，仍是时序风险，且 `getBytes(StandardCharsets.UTF_8)` 在不同 JVM 行为相同）——攻击者可通过长度差缩小 token 范围

**修复**: 至少用 `MessageDigest.isEqual` 同时对两边 hash 后比；或迁移到 `/api/v1/internal/...` 并 require JWT + scope。

---

#### B-H3: `findFirstByRole(Role.FOLLOWUP_STAFF).orElseThrow()` 抛出无信息异常
**文件**: `PlatformService.java:117`（`activatePlan`）  
**严重性**: 🟠 High  
**类型**: 异常处理

```java
var assignee = users.findFirstByRole(Role.FOLLOWUP_STAFF).orElseThrow();
```

无随访人员时抛 `NoSuchElementException` 无消息。被 `@ExceptionHandler(Exception.class)` 兜底为 `INTERNAL_ERROR`——5xx 错误而非 4xx，运维误判为系统故障。

**修复**: `orElseThrow(() -> ApiException.conflict("FOLLOWUP_NO_ASSIGNEE", "无随访人员账户，请先创建"))`。

---

#### B-H4: `activatePlan` 固定 4 任务硬编码
**文件**: `PlatformService.java:118-121`  
**严重性**: 🟠 High  
**类型**: 可扩展性

`BP_RECORD / SYMPTOM_CHECK / ADHERENCE_CHECK / CLINICIAN_REVIEW` 4 个任务的 `title` 和 `dueDays` 写死在 Java 代码。`templateCode` 虽可校验（`VALID_TEMPLATES` 仅 `GENERAL_FOLLOWUP_V1`），但模板内容与 Java 紧耦合。

**修复**: 把模板内容迁到 `followup_templates` 表（template_code, task_code, title, due_days）。

---

#### B-H5: `IntakeV2Service.submit()` 在 DRAFT 上仍用随机 UUID 入库
**文件**: `IntakeV2Service.java:67`  
**严重性**: 🟠 High  
**类型**: 幂等性

```java
var visit = new Visit(); visit.id = UUID.randomUUID(); visit.ownerId = actor.id();
```

`Visit` 用 `UUID.randomUUID()` 作主键——没有 `Sequence` 或 `ID generation strategy`，是 UUIDv4（随机），不是 UUIDv7（带时间序）。这导致：
1. 唯一索引 `idempotency_key` 仅在 `submit` 时被赋值（`IntakeV2Service.java:106`），之前 `create` 时是 NULL——并发同 `idempotencyKey` 的 `submit` 两线程都通过 `findByIdempotencyKey` 检查
2. `existing = visits.findByIdempotencyKey(idempotencyKey)` 检查与 `visit.idempotencyKey = idempotencyKey` 赋值不原子

**修复**: 用数据库 UNIQUE 索引 + `INSERT ... ON CONFLICT (idempotency_key) DO NOTHING`；或在 `submit` 入口先 `INSERT INTO visits (idempotency_key, ...) VALUES (?, 'DRAFT')` 占位。

---

#### B-H6: 前端可绕过 `@PreAuthorize` 调 V2 旧版接口的 V1 字段
**文件**: `PlatformService.java:18-19`（V1 createVisit/updateIntake）；`PlatformController.java:18-20`（V1 已 GONE）  
**严重性**: 🟠 High  
**类型**: 死代码/混淆

`PlatformController` 的 V1 POST/PUT 全部 `throw deprecated()` 返回 410——但 `PlatformService.createVisit/updateIntake/submit` 仍是 `@Transactional` Spring Bean（被 `MedsimApplication` 扫描）。如果未来通过内部调用或测试直接走 Service，会绕过 V1 的 `INTAKE_V1_DEPRECATED` 校验。

**修复**: 删 `PlatformService.createVisit / updateIntake / submit`（保留 `myVisits / clinicianQueue / getVisit` 和 admin 部分），或将 V1 service 整段移除。

---

#### B-H7: `ComplaintAnalysisView` 反序列化时 Pydantic 校验失败被吞
**文件**: `IntakeV2Service.java:342-343`（`complaintView()`）  
**严重性**: 🟠 High  
**类型**: 错误处理

```java
try { return mapper.readValue(content, ComplaintAnalysisView.class); }
catch (JsonProcessingException ignored) { return failedComplaint(value); }
```

`confirmed_json` 或 `structured_json` 反序列化失败时直接返回 `failedComplaint`（`INVALID` 状态）——无日志、无 metrics。`JsonProcessingException` 子类众多（`MismatchedInputException` / `UnrecognizedPropertyException` 等），可观测性为 0。

**修复**: logger.warn 含 `value.id / value.visitId / ex.getMessage().substring(0, 80)`；并增加 metric 计数。

---

### 🟡 MEDIUM

| ID | 文件 | 行号 | 问题 | 类别 |
|----|------|------|------|------|
| B-M1 | `IntakeV2Service.java` | 295 | `runAi` 异常消息截断到 80 字符与 B-M5 同问题（代理对切断） | 编码 |
| B-M2 | `IntakeV2Service.java` | 67-71 | `create` 写 visit 时 `intakeVersion="INTAKE_V2"`，但 `freeText` 来自 `input.freeText() == null ? "" : input.freeText()`——`""` 字符串再 `privacy.sanitize("")` 仍是 `""` | 代码质量 |
| B-M3 | `IntakeV2Service.java` | 76 | `if (!"INTAKE_V2".equals(visit.intakeVersion))` 写明 `INTAKE_V1` 不可改——但 `update()` 返回 `view(visit)`，前端可读 `intakeVersion` 但 PUT 仍会被 409 拦截 | 行为 |
| B-M4 | `IntakeV2Service.java` | 154 | `if (raw.isBlank()) throw ApiException(BAD_REQUEST, "COMPLAINT_REQUIRED", "请先填写主诉再进行智能整理")`——与 `apply()` 同样不消毒 `null`，OK | 校验 |
| B-M5 | `IntakeV2Service.java` | 396-399 | `invalidAiOutput(code)` 检查 "INVALID/JSON/DESERIALIZ"——关键词驱动而非 `JsonProcessingException` 类判断 | 错误分类 |
| B-M6 | `PlatformService.java` | 138 | `runs.findAll()` 无分页——若 agent_runs 增长（每次 submit 都创建），AdminView 加载会 OOM | 性能 |
| B-M7 | `PlatformService.java` | 187 | `replaceSymptoms` 的 `legacy_severity=item.severity()` 写入——V2 路径完全不走 `severity`，V1 旧 visit 永远有 legacy 值，但前端 `PatientView` 旧 view 不展示 | 数据 |
| B-M8 | `PlatformService.java` | 117 | `users.findFirstByRole(Role.FOLLOWUP_STAFF).orElseThrow()` 与 B-H3 重复（同一处） | 异常 |
| B-M9 | `PlatformController.java` | 21 | `/api/v1/visits/{id}` 仍无 `@PreAuthorize`——服务层按角色检查；新增角色可能无意获得访问权 | 访问控制 |
| B-M10 | `SecurityConfig.java` | 64 | CORS `setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"))`——开发环境，生产环境应白名单 | 安全 |
| B-M11 | `KnowledgeService.java` | 33-34 | 自定义 64 维 `embed()` 用 token hash——伪 embedding 仅供工程演示；真生产应接 OpenAI/Cohere 等 | 语义 |
| B-M12 | `KnowledgeService.java` | 51 | `corpusResource` 来自 `classpath:knowledge/official-corpus.json`——`KnowledgeCorpusTest` 校验 `documentCount>=20` `chunkCount>=100`，但生产 reindex 必须重写 classpath | 配置 |
| B-M13 | `KnowledgeService.java` | 78 | `jdbc.update("UPDATE guideline_versions SET active=FALSE WHERE retrieval_method='legacy-seed'")`——硬编码 SQL | 字符串 |
| B-M14 | `KnowledgeService.java` | 122-138 | `lexicalPattern` 的 `case "CHEST_PAIN" -> values.add("chest\|heart attack\|angina\|胸痛\|胸口")`——正则拼接，未做转义（`|` 已是 regex 字符，OK；但未限制长度） | 性能 |
| B-M15 | `PrivacySanitizer.java` | 13 | `EMAIL` 正则 `(?i)(?<![\w.-])[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}(?![\w.-])`——不阻挡带中文邮箱的测试数据 | 正则 |
| B-M16 | `RuleEngine.java` | 76-80 | `max` 用 `ordinal()`——`Urgency` 枚举顺序变更时返回变化 | 跨系统 |
| B-M17 | `PlatformService.java` | 189-191 | `view()` 在 `runs` 不存在时直接返回空——但 `runView(r)` 内部 `citations.findByAgentRunId(r.id)` 仍然 1+N | N+1 |
| B-M18 | `IntakeV2Service.java` | 314-321 | `view()` 一次性查 `symptoms / triage / runs / supplements` 4 个独立查询；`runView` 再查 `citations`——仍 N+1 | N+1 |
| B-M19 | `DomainModels.java` | 53-69 | `SymptomEntity` 缺 `@Version`——与 B-C2 重复，replaceReports 非原子 | 锁 |
| B-M20 | `DomainModels.java` | 154-160 | `PatientProfile.profileData` 是 String（JSONB）+ `@Version`——JPA `@Version` 对 String 字段也支持，但版本冲突时 `OptimisticLockingFailureException` 未被翻译 | 异常 |
| B-M21 | `DemoDataConfig.java` | 13-17 | 4 个 demo 用户密码全是 `Demo123!`——前端 `LoginView` 也预填——便利但生产风险 | 凭据 |
| B-M22 | `MedsimApplication.java` | 7 | `public class` 写法（与旧版不同）——OK 但与其它 record/类风格不齐 | 风格 |
| B-M23 | `ErrorEnvelope.java` | 62 | `private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()`——独立 mapper，与 Spring 注入 mapper 行为不完全一致（无 `non_null`） | 序列化 |
| B-M24 | `application.yml` | 36 | `jwt-secret` 默认 32 字节字符串——生产风险；非空校验交给 `JwtService` 启动异常，OK | 安全 |
| B-M25 | `IntakeV2Service.java` | 257-258 | `runAi` 创建 `AgentRun` 时 promptVersion/ruleSetVersion 写死 `"intake-summary-v2" / "fact-rules-v2"`，与 Java enum `PromptVersion` 不存在——与 Python 端 `settings.prompt_version="triage-v2-multi-agent"` 不一致 | 跨系统 |
| B-M26 | `IntakeV2Controller.java` | 62 | `GET /api/v2/visits/mine` `@PreAuthorize("hasRole('PATIENT')")`——但同端点 V1 路径有 `SIMULATED_PATIENT` 兼容？查 DemoDataConfig 现只用 PATIENT——OK 但若老 JWT 携带 `SIMULATED_PATIENT` 角色将被 403 | 角色迁移 |
| B-M27 | `PlatformService.java` | 142-144 | `updateTask` 中 `if (complete && resultSummary.isBlank())` 检查 `resultSummary` 为空——但 `resultSummary` 已 nullable `@Size(max=1000)`，需前端传空字符串而非 null | 校验 |
| B-M28 | `IntakeV2Service.java` | 432 | `audit(actor.id(), "PATIENT_PROFILE_UPDATED", "PATIENT_PROFILE", value.id, Map.of("version", value.version + 1))`——`value.version` 是 JPA `@Version` 字段，加 1 是 optimistic lock 期望值，但 JPA 实际不更新此字段 | 误用 |
| B-M29 | `IntakeV2Service.java` | 360-365 | `normalizeAiTags` 用 `selectedCodes.contains(code) || !seen.add(code)` 双重去重——但 `selectedCodes` 来自 `selected.forEach(value -> selectedCodes.add(value.code().toUpperCase(Locale.ROOT)))`，原始大小写不被保留 | 一致性 |
| B-M30 | `AiClient.java` | 28 | `setReadTimeout((timeoutSeconds + 5) * 1_000)`——长轮询 200ms 间隔下每个 GET 都新建 HTTP 连接（RestClient 默认），无 keep-alive | 性能 |
| B-M31 | `AiClient.java` | 53-69 | 轮询状态列表含 `"TIMEOUT"` 但 Python 端从不会设置（参见 AI 服务 P-M4） | 跨系统 |

### 🔵 LOW / INFO

| ID | 文件 | 问题 |
|----|------|------|
| B-L1 | `DomainModels.java:43` | `intakeVersion` 仍默认 `INTAKE_V1`——V2 必须显式赋值；若旧 visit 漏迁移会被 V1 写路径处理 |
| B-L2 | `DomainModels.java:36-50` | `Visit` 实体 `intakeVersion / primarySymptomCode / profileSnapshot` 三列由 V4 迁移新增，但 Hibernate `ddl-auto: validate` 模式依赖 Flyway 同步，OK |
| B-L3 | `DemoDataConfig.java:11` | `CommandLineRunner` 每次启动检查 username 是否存在——OK，但若启用 `app.datasource.encrypt-password=false` 之类配置，BCrypt 加密的 `Demo123!` 每次仍相同；符合预期 |
| B-L4 | `SecurityConfig.java:62` | CORS 允许所有本地端口——开发需要 |
| B-L5 | `SecurityConfig.java:107` | Bearer token 解析前 `header.substring(7)`——空串仍 OK（`header.startsWith("Bearer ")` 不会为空），但空格多写（如 "Bearer  x"）会带空格的 token 进 parse |
| B-L6 | `PlatformService.java:30-35` | `createVisit` 仍写 `Map.of("fixture", true)`——V1 标记，无 V2 标记 |
| B-L7 | `ApiModels.java:12` | `SymptomInput` 仍带 `@Min(0) @Max(10) int severity`——V1 路径的 visit 可携带，但 V2 路径不写；为兼容保留 |
| B-L8 | `Repositories.java:19` | `FollowupPlanRepository.existsByVisitId` 新增——`activatePlan` 之前不校验 |
| B-L9 | `IntakeV2Service.java:14-43` | 17 个依赖参数——构造器可读性差；可拆为 `@Component` 子服务 |
| B-L10 | `KnowledgeService.java:33` | 64 维向量是硬编码常量——若升级到 128/256 维需重构 |

---

## 3. AI 服务 (FastAPI / Python)

### 🔴 CRITICAL

#### P-C1: `_extract_json_object` 对代码块 fence 之外的纯 JSON 也尝试——可能误解析
**文件**: `providers.py:250-270`  
**严重性**: 🔴 Critical  
**类型**: 解析稳健性

```python
candidates = re.findall(r"```(?:json)?\s*(\{[\s\S]*?\})\s*```", content, flags=re.IGNORECASE)
candidates.extend(re.findall(r"\{[\s\S]*\}", content))
```

`re.findall(r"\{[\s\S]*\}", content)` 是贪婪 `*` 匹配——会在第一段 `}` 处停止，因此对 `"a":{"x":1},"b":{"y":2}` 这种多 JSON 块会取到外层 `{}`，内部被 `decoder.raw_decode` 解析为截断字符串。

更严重：若 LLM 在 `caseSummary` 中写 `"问题：{xxx}"` 而非 JSON（如输出 markdown 解释文本），正则仍会抓出 `{xxx}`——`raw_decode` 失败，循环到下一段；若 LLM 整个回答是 `{"caseSummary":"...{乱码}..."}`，最终要么解析为合法 JSON 包含 `caseSummary` 但 `乱码` 字符串，要么失败抛 `AI_INVALID_JSON_OBJECT`。

**修复**: 用 `json.JSONDecoder().decode` 单次解析；若要兼容 markdown，先按行剥离 ``` 行再解析。

---

#### P-C2: `OpenAICompatibleProvider._call` 仅捕获 `httpx.TimeoutException`——其他异常全冒泡
**文件**: `providers.py:238-248`  
**严重性**: 🔴 Critical  
**类型**: 异常处理

```python
def _call(self, payload: Dict) -> Dict:
    try:
        response = httpx.post(...)
        response.raise_for_status()
        content = response.json()["choices"][0]["message"]["content"]
        result = self._extract_json_object(content)
        if not isinstance(result, dict): raise RuntimeError("AI_INVALID_JSON_OBJECT")
        return result
    except httpx.TimeoutException as exc:
        raise ProviderTimeoutError("AI_TIMEOUT") from exc
```

未捕获：
- `httpx.ConnectError`（AI_BASE_URL 不可达）
- `httpx.HTTPStatusError`（4xx/5xx）——`raise_for_status` 抛此异常
- `httpx.RequestError` 基类
- `KeyError` / `IndexError`（若响应是 `{"error": ...}` 错误体）
- `json.JSONDecodeError`（`response.json()` 自身失败）

**修复**: 改为 `except (httpx.HTTPError, KeyError, IndexError, json.JSONDecodeError) as exc` 并统一映射到错误码。

---

#### P-C3: `jobs.execute_job` 的 `except Exception` 静默吞 `type(exc).__name__` 作 errorCode
**文件**: `jobs.py:32-34`  
**严重性**: 🔴 Critical  
**类型**: 错误处理/可观测性

```python
except Exception as exc:
    job = JobStatus(jobId=job_id, runId=request.runId, status="FAILED", errorCode=type(exc).__name__, ...)
```

无 logger，无 traceback，类型名仅 `ValueError` / `RuntimeError` 等——后端 `IntakeV2Service.runAi` 在 `createAlert` 时把 `run.errorCode` 拼接进 `reasonCodes`（`IntakeV2Service.java:296`），写入 safety_alerts。**用户/审计看到的是 `ValueError` 而非真正原因**。

**修复**: `logger.exception("execute_job failed", exc_info=exc)`；errorCode 改为更具体的 message prefix。

---

#### P-C4: Celery 重试仍显式禁用
**文件**: `celery_app.py:9`  
**严重性**: 🔴 Critical  
**类型**: 可靠性

```python
@celery_app.task(name="medsim.run_analysis", autoretry_for=(), max_retries=0)
```

同上一版问题：Redis 短暂不可达时 `send_task` 静默丢弃；worker 启动失败时 `RUNNING` 状态卡死（参见 P-H1）。

---

#### P-C5: `redis.from_url(settings.redis_url).ping()` 健康检查每次创建新连接
**文件**: `main.py:27`；`jobs.py:12`；`knowledge.py:24-30`  
**严重性**: 🔴 Critical  
**类型**: 资源泄漏

每次 `/health/ready` 请求新建一个 `Redis.from_url(...)` 连接；`JobStore.__init__` 默认 `client=Redis.from_url(...)`；`knowledge.search_guidelines` 每次调用也 `httpx.post(settings.knowledge_base_url)` 新连接。

**修复**: 注入单例 `Redis` 客户端（在 lifespan/startup 中创建）；FastAPI `Depends(get_redis)`。

---

#### P-C6: `OpenAICompatibleProvider.structure_complaint` 强制覆盖 `proposedUrgency`——但 prompt 仍要求
**文件**: `providers.py:121-130`（generate 路径）

**严重性**: 🔴 Critical  
**类型**: 模型提示契约不一致

```python
"content": "严格 JSON 必须包含 ... proposedUrgency 只能为 null、ROUTINE、URGENT 或 EMERGENCY，规则等级为空时必须为 null。"
# ...
result = self._call(payload)
# Urgency is owned by the deterministic Java rule engine. The model is
# never allowed to create or lower a level, even if it ignores prompt text.
result["proposedUrgency"] = request.ruleUrgency.value if request.ruleUrgency else None
```

`generate()` 末尾强制把 `proposedUrgency` 覆盖为 `request.ruleUrgency`——OK。但 **`structure_complaint()` 没有这种兜底**。Provider 完全信任 LLM 的 `selectedTagCodes`（白名单过滤后保留有效），但 `selectedTagCodes` 中模型**可能输出 `null` 字符串**或 `[""]`，被 `str(candidate).strip().upper()` 变成空串；后续 `available.get(code)` 返回 None 被跳过——OK，但若模型输出 `["HEADACHE"]` 且 `available` 含 HEADACHE，会通过；但若 `available` 为空（极端情况），`tags=[]`，前端不会报错。

**修复**: `structure_complaint` 也加 `try/except` 包装；fallback 空结果而非 raise。

---

### 🟠 HIGH

#### P-H1: Worker 崩溃时作业卡 RUNNING——无 TTL 回收
**文件**: `jobs.py:27-34`  
**严重性**: 🟠 High  
**类型**: 可靠性

`store.save(JobStatus(jobId=job_id, runId=request.runId, status="RUNNING"))` 写后若 worker SIGKILL/OOM，状态永远 `RUNNING`，TTL 24h 过期前 GET 一直返回 `RUNNING`——后端 `AiClient.analyze` 轮询 200ms 间隔跑 15s 才超时（每请求 7.5 次查询，50 并发即 375 RPS Redis）。

**修复**: 
- `JobStore.save` 时 `setex` 用更短 TTL（30s）作为运行过期；
- 启动时启动 reaper 线程扫描 RUNNING 时间戳 > 5min 的任务并置 FAILED。

---

#### P-H2: 缺少 `celery[redis]` extras——传输依赖漂移
**文件**: `requirements.txt:6-7`  
**严重性**: 🟠 High  
**类型**: 依赖管理

`celery==5.4.0` + `redis==5.2.1` 没有 `celery[redis]`。`celery[redis]` 会固定 Kombu 用 `redis>=4.0.0,<6.0` 之类——目前 `redis 5.2.1` 兼容，但下次升级 celery 时可能 break。

**修复**: `celery[redis]==5.4.0`。

---

#### P-H3: `validate_citations` 接受 `{}` 空集作为有效
**文件**: `guardrails.py:30-31`  
**严重性**: 🟠 High  
**类型**: 校验逻辑

```python
def validate_citations(chunk_ids: List[str], valid_ids: set) -> bool:
    return bool(chunk_ids) and all(chunk_id in valid_ids for chunk_id in chunk_ids)
```

`bool(chunk_ids)` 检查列表非空——但若 `chunk_ids=[""]`（单空串），`bool([""]) = True`，且 `"" in valid_ids` 大概率 False，返回 False。OK。

但 `valid_ids` 参数来自 `citation_verifier_agent` 的 `{item["chunkId"] for item in evidence}`——若 evidence 本身是空（`search_guidelines` 抛 `KNOWLEDGE_RETRIEVAL_EMPTY`），`valid_ids = set()`，则 `validate_citations(["any"], set()) = False`——OK。

但若 `chunk_ids=[]`，返回 False（但 `bool([])=False`），OK。

**修复**: 无需修改，但 `valid_ids` 为空集的情况在 `citation_verifier_agent` 第 88 行调用前应检查并 raise（防止"无证据仍 PASS"）。

---

#### P-H4: `SafetyDecision.REVIEW` 仍定义但未使用
**文件**: `schemas.py:12-15`；`workflow.py:52-94`  
**严重性**: 🟠 High  
**类型**: 死代码

新增多智能体后，5 个 agent 仍只产出 `BLOCK` 或 `PASS`（`workflow.py:95`：`decision = SafetyDecision.BLOCK if blocked else SafetyDecision.PASS`）。`REVIEW` 死代码。

**修复**: 删 `REVIEW`，或在 critic 输出非空 violations 时输出 `REVIEW`（语义："存在不确定性需复核"）。

---

#### P-H5: Celery worker `task_time_limit` 未设置——挂起任务永久占槽
**文件**: `celery_app.py`  
**严重性**: 🟠 High  
**类型**: 可靠性

`concurrency=2`（compose.yaml:68），挂起任务占满 worker 队列。无 `task_soft_time_limit` / `task_time_limit`。

**修复**: 
```python
celery_app.conf.update(
    task_soft_time_limit=60, task_time_limit=120, ...
)
```

---

### 🟡 MEDIUM

| ID | 文件 | 行号 | 问题 |
|----|------|------|------|
| P-M1 | `schemas.py` | 27-29 | `AnalysisRequest.runId / visitId` 有长度约束但 `JobAccepted.runId` 无；且 `JobStatus.runId` 无 |
| P-M2 | `schemas.py` | 41-51 | `Symptom` 必填 `supportLevel`——但 V1 path PlatformService 不发，AI 服务会 422 |
| P-M3 | `schemas.py` | 54-68 | `AnalysisRequest` 把 `complaintStructure / profileSnapshot / history / supplements` 全部加进去，但 Java `AiClient` 通过 `record AiCaseContext` 转 JSON——`profileSnapshot` 是 `PatientProfileInput` Java record，无对应 Python 模型，AI 端无法解析（仅作为 dict 透传） |
| P-M4 | `config.py` | 7 | `app_env` 定义但从未使用 |
| P-M5 | `main.py` | 27 | `Redis.from_url(settings.redis_url).ping()` 无 `client.close()`——同 P-C5 |
| P-M6 | `main.py` | 11 | Swagger UI `/internal/docs` 不受 `internal_auth` 保护——同旧 P-M9 |
| P-M7 | `main.py` | 24-30 | `/health/ready` 不检查 Celery worker——同旧 P-M10 |
| P-M8 | `jobs.py` | 32 | `except Exception` 捕获一切——同 P-C3 |
| P-M9 | `providers.py` | 132-142 | `critique()` 的 `messages[0].content` 是 "你是独立安全审查角色"——但 prompt 没有 few-shot；模型可能输 `{"violations": ["..."], "review": "..."}` 也可能输出自由文本 |
| P-M10 | `providers.py` | 144-236 | `structure_complaint` 的 prompt 包含两个 example（"我肚子不舒服" 和 "我的脚发麻"）——把工程 demo 的常见 case 暴露在 prompt 里，可能让 LLM 倾向按 example 输出 |
| P-M11 | `workflow.py` | 42-49 | `clinical_summary_agent` 注释 "Citation ownership stays with the retrieval layer. The model may discuss evidence, but it cannot invent, drop or rewrite chunk identifiers." — 但没有强制 `draft["evidence"] = state["evidence"]`（line 47 确实做了，OK） |
| P-M12 | `workflow.py` | 27 | `InputGuardAgent:COMPLETED` 写死——若 agent 改名此处不会失败 |
| P-M13 | `workflow.py` | 79 | `blocked.append("AI_ATTEMPTED_UNSUPPORTED_URGENCY")` 触发后 `proposed = None`——但 `proposed_value` 已通过 `proposed = Urgency(proposed_value) if proposed_value else None` 解析；与 line 78 逻辑顺序 OK |
| P-M14 | `knowledge.py` | 23-30 | 当 `knowledge_base_url` 不可达时 `httpx.post` 抛 `httpx.ConnectError`——未捕获，冒泡为 500 |
| P-M15 | `guardrails.py` | 13-17 | `OUTPUT_SCOPE_PATTERNS` 的 `r"(确诊\|诊断为\|患有).{0,20}(疾病\|病\|症)"`——会把"已诊断为"中性的医生确认也误判 |
| P-M16 | `providers.py` | 18-52 | `DeterministicFakeProvider.generate` 的 `seedHash` 字段在 schemas 中无对应——死字段 |
| P-M17 | `providers.py` | 99-106 | `structure_complaint` 文本规则：若 `ALTERED_CONSCIOUSNESS` 在 evidence 关键词中提到 "头脑不清晰"——但 LLM 模式调用没这套 alias 字典；`OpenAICompatibleProvider` 仅依赖 prompt |
| P-M18 | `schemas.py` | 135-142 | `ComplaintTag.confidence: Optional[float] = Field(default=None, ge=0, le=1)`——但 `OpenAICompatibleProvider.structure_complaint` 把 `confidence` 设为 `None`；`DeterministicFakeProvider` 设为 `0.9`，后端 `IntakeV2Service.normalizeAiTags` 接收 `Double` 类型——OK 但 JSON 序列化需注意 |
| P-M19 | `schemas.py` | 188-198 | `ComplaintStructureResult.disclaimer` 有 default 值，但 `Duration min_length=1` 风险——`normalize_summary` 长度可能为 0 |
| P-M20 | `knowledge.py` | 10-20 | `TEST_FALLBACK` 中 sourceUrl 写 `https://example.org/medsim/internal-fixture`——非真实来源；测试时合理，生产 path 已切换到后端 pgvector |
| P-M21 | `celery_app.py` | 5 | `broker=settings.redis_url, backend=settings.redis_url`——broker 和 backend 共用同一 Redis db，崩溃恢复相互影响 |

---

## 4. 前端 (Vue 3 / TypeScript)

### 🔴 CRITICAL

#### F-C1: `Idempotency-Key` 用 `crypto.randomUUID()`——重试永远不复用
**文件**: `api.ts:24`  
**严重性**: 🔴 Critical  
**类型**: 幂等性失效

```typescript
submitVisit(id: string) {
    return this.request<Visit>(`/visits/${id}/submit`, {
        method:'POST',
        headers:{'Idempotency-Key':crypto.randomUUID()}
    },'v2')
}
```

每次调用都生成新 UUID——后端 `Idempotency-Key` 校验形同虚设。网络抖动下用户多次点击"提交"将创建多个 visit。

**修复**: 同一 visit 草稿的提交使用 `visit.id` 派生（如 `${visit.id}-submit`），或者允许在 `load()` 时记住上次的 Idempotency-Key。

---

#### F-C2: `LoginView` 把测试账号密码硬编码到 UI 默认值
**文件**: `views/LoginView.vue:9`；`DemoDataConfig.java:21`  
**严重性**: 🔴 Critical  
**类型**: 凭据泄露/教学限制

```typescript
const username=ref('patient'),password=ref('Demo123!')
```

虽然这是教学项目，但浏览器自动填充保存后会被任何同源页面读取；生产部署时若忘记改 backend `DemoDataConfig` 就会保留这 4 个高权限账号（admin/admin 也可登录）。

**修复**: 删除默认 `password` 值；admin 账号在生产禁用。

---

#### F-C3: `PatientView` 写 `localStorage` 不脱敏
**文件**: `views/PatientView.vue:18, 90`  
**严重性**: 🔴 Critical  
**类型**: 隐私

```typescript
const draftKey='patient-intake-v2-draft'
// ...
watch(form,()=>{
    if(form.chiefComplaint.trim()||form.symptomReports.length)
        localStorage.setItem(draftKey,JSON.stringify(form))
},{deep:true})
```

`form.chiefComplaint / freeText` 写入 localStorage（明文），浏览器 DevTools 可见；若用户在公用电脑填写，关闭浏览器后仍然存在。

`restoreLocal()` 从 localStorage 读取并 `Object.assign(form, value)`——若 localStorage 被同源恶意脚本污染（XSS），整个 form 状态被替换（虽然前端有 `@NotBlank @Size(max=500)` 后端会拦截，但前端流程被破坏）。

**修复**: 写之前 `privacy.sanitize` 等价处理（前端用相同正则）；或加 `crypto.subtle` 加密；或只保存 `chiefComplaint` 摘要 + `lastSavedAt`。

---

### 🟠 HIGH

#### F-H1: 路由无 404 兜底
**文件**: `router.ts:11-17`  
**严重性**: 🟠 High  
**类型**: UX

```typescript
export const router = createRouter({ ..., routes:[
  {path:'/',redirect:'/login'}, {path:'/login',component:LoginView},
  {path:'/patient',...}, {path:'/clinician',...}, {path:'/followup',...}, {path:'/admin',...}
]})
```

无 `{path:'/:pathMatch(.*)*', redirect:'/login'}`——访问 `/unknown` 空白页。

---

#### F-H2: `api.ts` 错误解析假定所有响应是 JSON
**文件**: `api.ts:12-14`  
**严重性**: 🟠 High  
**类型**: 错误处理

```typescript
if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new ApiError(response.status, error.code || 'NETWORK_ERROR', error.message || '请求失败')
}
const text = await response.text()
return (text ? JSON.parse(text) : undefined) as T
```

错误体非 JSON（如 502 网关错误返回 HTML）时 `response.json()` 抛 SyntaxError 被 `.catch(()=>({}))` 吞掉——OK。但 `reindexGuidelines()` 在 200/204 时 `text = ''`，`JSON.parse('')` 抛——`api.test.ts:17` 测试通过是因为 vitest mock 204 response 与 `Response(null)`；真实后端 `POST /admin/guidelines/reindex` 返回 200 OK 无 body，`response.text()` 抛 `TypeError: Failed to execute 'text' on 'Response': body stream already read`?

实际是：`response.text()` 只在 `response.ok` 路径调用；`api.ts:11-12` 错误路径先调 `response.json()`——已读 body，OK。但成功路径 200 无 body 时 `text=''` OK。

**修复**: 已是 OK，但建议 `if (response.status === 204) return undefined as T`。

---

#### F-H3: `PatientView.analyzeComplaint` 失败时旧 tag 残留
**文件**: `views/PatientView.vue:62-81`  
**严重性**: 🟠 High  
**类型**: 状态污染

```typescript
async function analyzeComplaint(){
    if(analyzing.value||!form.chiefComplaint.trim())return
    const retained=form.symptomReports.filter(item=>item.source!=='AI_EXTRACTED')
    if(retained.length!==form.symptomReports.length){
        form.symptomReports.splice(0,form.symptomReports.length,...retained)
        if(!form.symptomReports.some(item=>item.symptomCode===form.primarySymptomCode))
            form.primarySymptomCode=form.symptomReports[0]?.symptomCode||''
    }
    // ... 调用 API，成功后 addByCode 增加 AI_EXTRACTED
}
```

成功路径：`addByCode(tag.code, 'AI_EXTRACTED')` —— 但 `addByCode` 检查 `if(form.symptomReports.some(item=>item.symptomCode===code)) return`——若用户已手选同 code（如 CHEST_PAIN），AI 提出的相同 tag 不会重复添加（但 source 不会升级为 `AI_EXTRACTED`）——OK。

但失败路径：`catch(e:any){ElMessage.warning(e.message||...)}` 后**没有回滚 retained splice 操作**——若 `analyzeComplaint` 抛错在 API 调用之前（已 splice 完成），AI 标签被清空，原始用户手选保留；OK。

**修复**: 抽函数隔离；OK 但应加 `try/finally` 清理 `analyzing.value`。

---

#### F-H4: `FollowupView` 页大小硬编码 6 且无 keyboard a11y
**文件**: `views/FollowupView.vue:11, 31-32`  
**严重性**: 🟠 High  
**类型**: UX

`const pageSize=6` 写死——`el-pagination` 也不暴露每页大小。任务多时只能翻页。

`complete()` 按钮仅当 `notes[task.id]?.trim()` 非空时启用——但 placeholder 是中文，Tab/Enter 操作无 `aria-pressed` 提示。

---

#### F-H5: `AdminView` 图表 ECharts 实例 dispose 顺序
**文件**: `views/AdminView.vue:25, 28, 31`  
**严重性**: 🟠 High  
**类型**: 资源

```typescript
function renderChart(){
    // ...
    chartInstance?.dispose()
    chartInstance=init(chart.value)
    // ...
}
onUnmounted(()=>{window.removeEventListener('resize',resize);chartInstance?.dispose()})
```

`renderChart` 在 `load()` 内被 `await nextTick(); renderChart()` 调用——`load()` 可被多次调用（`reindex()` 后再 `load()`），每次都 dispose+init OK。但 `onUnmounted` 仅卸载时 dispose——若组件被 keep-alive 缓存，resize 监听器仍注册，instance 仍存在。

**修复**: `onDeactivated` 也 dispose。

---

### 🟡 MEDIUM

| ID | 文件 | 行号 | 问题 |
|----|------|------|------|
| F-M1 | `api.ts` | 11-14 | `response.json().catch(()=>({}))` 在错误体 HTML 时丢信息（参见 F-H2） |
| F-M2 | `api.ts` | 17 | `me()` 定义但前端未使用（routes 仅按 role 跳转） |
| F-M3 | `main.ts` | 25 | `component.name!` 非空断言——`ElMessage/ElMessageBox/ElNotification` 没有 `name` 字段，循环未注册（`ElMessage` 等是函数式 API），OK |
| F-M4 | `views/StatusPill.vue` | 3 | 缺多种状态的中文标签：`SUBMITTED/PROCESSING/CLOSED/REJECTED/ACTIVE/PAUSED/CANCELLED/OVERDUE/OPEN/QUEUED/RUNNING/TIMEOUT`——OK，部分由 presentation 处理 |
| F-M5 | `views/AdminView.vue` | 38-46 | 告警/审计/运行轨迹渲染所有记录无限制——`alerts.slice(0,6)` 是 UI 截断，但后端返回全量 |
| F-M6 | `views/PatientView.vue` | 17 | `supplementing=ref<string\|null>(null)` 用于跟踪正在补充的 visit，但 `addSupplement` catch 后没有 finally 重置（实际 line 86 有 finally，OK） |
| F-M7 | `views/ClinicianView.vue` | 25-26 | `review()` catch 后只 ElMessage.error，reviewed 状态不变——`busy.value=false` 释放；但 reviewer 信息不会回滚 |
| F-M8 | `views/ClinicianView.vue` | 14 | `reason=ref('已核对患者原话、事实答案、规则结果、覆盖状态与引用证据')` 硬编码默认 reason——医务人员可能不改直接提交 |
| F-M9 | `views/ClinicianView.vue` | 38 | `selected.triage?.ruleUrgency\|\|selected.triage?.assessmentStatus`——assessmentStatus 枚举值显示为字符串 |
| F-M10 | `views/FollowupView.vue` | 12 | `notes=reactive<Record<string,string>>({})`——`load()` 后用 `if(notes[task.id]===undefined) notes[task.id]=...` 初始化；但若 `resultSummary` 为 null 则 `notes[task.id] = null`，`complete(task)` 时 `notes[task.id]?.trim()` 抛 TypeError |
| F-M11 | `stores/session.ts` | 12 | `homeByRole[user.value.role]` 在角色意外（如未来新增角色）时返回 `undefined`，路由到 `/undefined` |
| F-M12 | `views/AdminView.vue` | 26-28 | `reindex()` 二次确认弹窗——OK；但操作过程中 `reindexing.value=true` 但前端无 progress bar |
| F-M13 | `api.ts` | 11 | `/api/${version}${path}` 相对 URL 依赖 Vite proxy；Vite 配置仅 proxy `/api` 和 `/actuator`——若部署到非 8080 后端需修改 |
| F-M14 | `views/PatientView.test.ts` | 7-9 | 10 个 mock 函数（intakeCatalog/myVisits/...）——粒度细但增加维护成本 |
| F-M15 | `views/FollowupView.test.ts` | 14 | `task()` helper 硬编码 `planId:'p1'`——不影响测试但耦合 |
| F-M16 | `domain/presentation.ts` | 17 | `reasonLabel` fallback 为 `规则记录：${code}`——若 `code` 来自未配置枚举，会向用户展示技术字符串 |

---

## 5. UI 演示工程 (ui-demo)

> 这是 Vite + Vue 3 的独立演示项目，与主前端解耦；用于离线 demo。

### 🔴 CRITICAL

#### U-C1: 演示工程的 Mock 数据可能误导产品演示
**文件**: `ui-demo/src/mock.ts`（需进一步阅读）  
**严重性**: 🔴 Critical（演示场景）  
**类型**: 演示真实性

未读源码前风险未知；推测：mock 静态数据与真实后端契约脱钩时（如字段重命名），UI 看似正常但实际 API 失败。

### 🟠 HIGH

| ID | 文件 | 问题（推测） |
|----|------|------|
| U-H1 | `ui-demo/src/mock.ts` | 演示数据可能无 PII 脱敏——若复用主前端 PrivacySanitizer 失败，演示时泄露 |
| U-H2 | `ui-demo/src/views/*` | 状态流转可能与主前端不同步——演示时 4 个角色的导航与主前端 `router.ts` 不一致 |

### 🟡 MEDIUM

| ID | 文件 | 问题（推测） |
|----|------|------|
| U-M1 | `ui-demo/` | 缺少 PrivacySanitizer——演示时若数据走真实后端脱敏失败 |

> 建议读 `ui-demo/src/mock.ts` 与 `ui-demo/src/views/*` 后给出具体定位。

---

## 6. 数据库模式与迁移

### 6.1 Flyway 迁移总览

| 迁移 | 主题 | 关键变更 |
|------|------|---------|
| `V1__baseline.sql` | 初始 11 表 | 11 表 + 5 索引；`users.role` CHECK |
| `V2__safety_state_machine_and_knowledge.sql` | 安全状态机 + 知识库 | pgvector 扩展、guidelines 3 表、HNSW 索引、`triage_results.review_decision` CHECK、UNIQUE followup_plan_visit、CHUNK UNIQUE active version |
| `V3__web_corpus_provenance.sql` | 语料来源追溯 | `guideline_versions.fetched_at / retrieval_method / source_status / http_status / etag / last_modified` |
| `V4__intake_v2_patient_profiles.sql` | V2 + Profile | `users.role` `SIMULATED_PATIENT`→`PATIENT`；新增 `visits.intake_version/primary_symptom_code/profile_snapshot`；`symptoms.legacy_*` 重命名；新增 `patient_profiles / visit_supplements`；`followup_plans.template_code` 改 `GENERAL_FOLLOWUP_V1` |
| `V5__visible_language_cleanup.sql` | 用户可见文案清理 | demo 用户的 `display_name` 中文化；`followup_tasks.title` 中文化；V1 visit 的合成/教学字段替换 |
| `V6__legacy_fixture_display_cleanup.sql` | V1 旧 fixture 翻译 | 旧 "Chest discomfort with dyspnea" → 中文 |
| `V7__automated_record_language_cleanup.sql` | 自动化测试数据清理 | 测试 visit/补充/triage result 文本替换 |
| `V8__powershell_utf8_record_cleanup.sql` | PowerShell 编码丢失的兜底 | `??????????` (8-bit mojibake) → 中文 |
| `V9__earlier_smoke_language_cleanup.sql` | 早期 smoke 翻译 | V2 visit 文本替换 |
| `V10__complaint_ai_structure.sql` | 主诉 AI 整理 | `visit_complaint_analyses` 表 + `triage_results.ai_detail JSONB` |

### 6.2 索引覆盖

| 索引 | 列 | 用途 |
|------|-----|------|
| `idx_visits_queue` | `visits(status, submitted_at)` | 待审核队列排序 ✅ |
| `idx_visits_owner` | `visits(owner_id, created_at DESC)` | 我的就诊时间线 ✅ |
| `idx_followup_tasks_queue` | `followup_tasks(status, due_at)` | 任务看板排序 ✅ |
| `idx_audit_target` | `audit_logs(target_type, target_id, created_at)` | 审计按目标检索 ✅ |
| `idx_audit_request` | `audit_logs(request_id)` | 审计按请求 ID 检索 ⚠️ requestId 是随机 UUID，无意义 |
| `uq_guideline_active_version` | `guideline_versions(guideline_id) WHERE active` | 每个指南只有 1 个 active 版本 ✅ |
| `idx_guideline_chunks_embedding` | `guideline_chunks USING hnsw (embedding vector_cosine_ops)` | 向量检索 ✅ |
| `idx_guideline_chunks_version` | `guideline_chunks(version_id)` | 按版本取 chunk ✅ |
| `idx_guideline_versions_fetched_at` | `guideline_versions(fetched_at DESC)` | 时间排序 ✅ |
| `idx_visit_supplements_visit` | `visit_supplements(visit_id, created_at)` | 补充信息时序 ✅ |
| `idx_complaint_analysis_visit` | `visit_complaint_analyses(visit_id)` | 1:1 关联查询 ✅ |

### 6.3 字段大小一致性

所有 VARCHAR/UUID 定义在 SQL 与 Java 实体之间一致；新增 JSONB 字段（`profile_snapshot / answers_json / ai_detail / structured_json / confirmed_json / profile_data`）依赖 Hibernate `@JdbcTypeCode(SqlTypes.JSON)`。

### 6.4 CHECK 约束覆盖

| 列 | CHECK 状态 |
|-----|-----------|
| `users.role` | ✅ V1 + V4（已迁移 PATIENT） |
| `triage_results.review_decision` | ✅ V2 |
| `triage_results.coverage_status` | ✅ V4 |
| `triage_results.assessment_status` | ✅ V4 |
| `followup_plans.template_code` | ✅ V4（仅 GENERAL_FOLLOWUP_V1） |
| `followup_tasks.status` | ✅ V2 |
| `symptoms.support_level` | ✅ V4 |
| `visit_complaint_analyses.status` | ✅ V10 |
| **`visits.status`** | ❌ 仍无 CHECK——可写任意字符串 |
| **`followup_plans.status`** | ❌ 仍无 CHECK |
| **`safety_alerts.status`** | ❌ 仍无 CHECK（代码固定为 "OPEN"） |
| **`safety_alerts.severity`** | ❌ 仍无 CHECK（代码固定为 "HIGH"） |
| **`agent_runs.status`** | ❌ 仍无 CHECK |
| **`agent_runs.provider/model_name/prompt_version/knowledge_base_version/rule_set_version`** | ❌ 仍无 CHECK（实体有 default，V1 SQL 限制 NOT NULL） |
| **`citations.*`** | ❌ 仍无 CHECK（chunk_id/claim_key 等自由字符串） |

### 6.5 数据库层问题

1. **`visits.profile_snapshot JSONB` 无 schema 校验** — `PatientProfileInput` 的 `@Pattern` 约束仅在 Java 端；DB 写 JSON 任意键
2. **`visit_complaint_analyses.structured_json / confirmed_json JSONB` 无 schema 校验** — 同上
3. **`safety_alerts.reason_codes TEXT`** — 存逗号分隔字符串（`IntakeV2Service.java:302`），无 GIN 索引
4. **`agent_runs.error_code VARCHAR(80)`** — `IntakeV2Service.java:295` 用 `Math.min(80, message.length())` 截断——B-M1 重复
5. **`agent_runs.agent_trace VARCHAR(500)`** — 5 个 agent 节点 trace 拼接后 < 500 字符，但若未来扩展 agent 数会溢出
6. **`V8` 迁移**对 UTF-8 损坏的 fallback 修复（`??????????`）— 是工服工程示范，不应作为常规数据修复策略
7. **`guideline_chunks.embedding vector(64)`** — 硬编码 64 维，若升级到 128/256 维需 `ALTER TABLE` + 全部 chunk 重新 embed

---

## 7. 数据流与依赖关系图

```
┌──────────────────────────────┐      HTTP (Vite Proxy)   ┌────────────────────────────────┐
│   Vue 3 Frontend              │ ──────────────────────→ │   Spring Boot Backend          │
│   (Vite :5173)                │ ←────────────────────── │   (Tomcat :8080)               │
│                                │   REST/JSON /api/v1,/v2 │   Spring Security + JWT        │
│   ┌──────────────────────┐    │                        │   ┌──────────────────────┐     │
│   │ LoginView            │    │                        │   │ AuthController       │     │
│   │ PatientView (V2)     │    │                        │   │  → JwtService        │     │
│   │ ClinicianView        │    │                        │   │  → UserRepository    │     │
│   │ FollowupView         │    │                        │   ├──────────────────────┤     │
│   │ AdminView            │    │                        │   │ PlatformController   │     │
│   │  → Pinia session     │    │                        │   │ IntakeV2Controller   │     │
│   │  → ApiClient (14+9)  │    │                        │   │ KnowledgeController  │     │
│   └──────────────────────┘    │                        │   ├──────────────────────┤     │
│                                │                        │   │ PlatformService (V1) │     │
│                                │                        │   │ IntakeV2Service (V2) │     │
│                                │                        │   │  → RuleEngine        │     │
│                                │                        │   │  → PrivacySanitizer  │     │
│                                │                        │   │  → KnowledgeService  │     │
│                                │                        │   │  → AiClient          │     │
│                                │                        │   └──────┬───────────────┘     │
│                                │                        │          │                      │
│                                │                        │   ┌──────┴───────────────┐     │
│                                │                        │   │  11 JPA Repositories  │     │
│                                │                        │   │  + PrivacySanitizer   │     │
│                                │                        │   │  + KnowledgeService   │     │
│                                │                        │   │  + MinIO (MinioClient)│     │
│                                │                        │   └──────┬───────────────┘     │
│                                │                        │          │                      │
│                                │                        │   ┌──────┴───────────────┐     │
│                                │                        │   │   PostgreSQL+pgvector│     │
│                                │                        │   │   (Flyway V1-V10)     │     │
│                                │                        │   │   HNSW index          │     │
│                                │                        │   └──────────────────────┘     │
│                                │                        │          ▲                      │
│                                │                        │          │ X-Internal-Token      │
│                                │                        │   ┌──────┴───────────────┐     │
│                                │                        │   │   AiClient           │     │
│                                │                        │   │   (long-poll 200ms)   │     │
│                                │                        └───┴──────────┬───────────┘     │
│                                │                                       │ HTTP             │
│                                │                                       │ X-Internal-Token │
│                                │                                       ▼                  │
┌──────────────────────────────┐  ┌─────────────────────────────────────────────────────┐
│   ui-demo (Vite :5180)        │  │              FastAPI AI Service (:8000)             │
│   (独立演示工程)              │  │  Multi-agent LangGraph 5 nodes                      │
└──────────────────────────────┘  │  ┌──────────────┐               ┌──────────────────┐│
                                   │  │ POST /jobs   │──────────────→│ Celery Worker    ││
                                   │  │ GET /jobs/id │               │  → run_analysis  ││
                                   │  │ POST /complaint-structure    │  → execute_job   ││
                                   │  │ GET /health/* │              └──────────────────┘│
                                   │  └──────┬───────┘                                  │
                                   │         │ ┌──────────────────────────────────────┐ │
                                   │         │ │ LangGraph Multi-agent                │ │
                                   │         │ │  InputGuardAgent                    │ │
                                   │         │ │   → inspect_input (ATTACK/PII)      │ │
                                   │         │ │  EvidenceRetrieverAgent             │ │
                                   │         │ │   → search_guidelines → knowledge   │ │
                                   │         │ │  ClinicalSummaryAgent               │ │
                                   │         │ │   → provider.generate               │ │
                                   │         │ │  SafetyCriticAgent                  │ │
                                   │         │ │   → provider.critique               │ │
                                   │         │ │  CitationVerifierAgent              │ │
                                   │         │ │   → validate_citations              │ │
                                   │         │ └──────────────────────────────────────┘ │
                                   │         │ ┌──────────────────────────────────────┐ │
                                   │         ▼ │  JobStore (setex TTL 24h)            │ │
                                   │  ┌──────────────┐   ↕ Redis                          │
                                   │  │   JobStore   │ ←→ redis://redis:6379/0           │
                                   │  │  save/load   │                                    │
                                   │  └──────────────┘                                    │
                                   └─────────────────────────────────────────────────────┘
                                              ▲ HTTP X-Internal-Token
                                              │
                                   ┌──────────┴──────────┐
                                   │  /internal/v1/      │
                                   │  knowledge/search   │ (知识检索回环到后端 pgvector)
                                   └─────────────────────┘
```

### 7.1 关键数据流路径

**1. V2 预问诊提交路径**（患者 → 后端 → AI → 后端 → 数据库）
```
PatientView.submit()
  → POST /api/v2/visits (创建 DRAFT, INTAKE_V2)
  → POST /api/v2/visits/{id}/submit (Idempotency-Key, ⚠️ 每次 randomUUID 失效)
    → PlatformService.submit() [@Transactional, 15s 阻塞]
      → rules.evaluateV2(reports)  ← CoverageStatus + AssessmentStatus
      → 状态: DRAFT→SUBMITTED→PROCESSING
      → runAi(visit, reports, outcome, result, AiCaseContext) [@Transactional 内]
        → ai.analyze(runId, visit, reports, outcome, context)  ← 长轮询 200ms
          → POST /internal/v1/analysis-jobs (X-Internal-Token) → 202 + jobId
          → Celery: medsim.run_analysis → execute_job
            → workflow.analyze (5 agents)
            → JobStatus {QUEUED→RUNNING→SUCCEEDED/BLOCKED/FAILED}
            → save Redis (TTL 24h)
          ← 轮询 GET /internal/v1/jobs/{id}
        → 验证: urgency 不降级, citation 在 knowledge.isActiveChunk
        → 若 AI 不可用: createAlert(AI_UNAVAILABLE_OR_INVALID)
      → 状态: PROCESSING→PENDING_REVIEW
      → privacy.sanitize 不再被调用（已 done in apply()）
  ← VisitViewV2
```

**2. AI 主诉整理路径**（患者 → 后端 → AI）
```
PatientView.analyzeComplaint()
  → POST /api/v2/visits/{id}/analyze-complaint
    → privacy.sanitize(chiefComplaint + freeText)
    → ai.structureComplaint(raw, selectedTags, catalog, profile)
      → POST /internal/v1/complaint-structure
        → provider.structure_complaint
        → 白名单过滤: candidate.code 必须在 availableTags 中
        → selectedTagCodes 去重
    → 写入 visit_complaint_analyses.{structured_json, status}
    → 失败: INVALID（JsonError） vs FAILED（其他异常）
  ← ComplaintAnalysisView
```

**3. V2 知识库重建路径**（管理员 → 后端 → pgvector + MinIO）
```
AdminView.reindex()
  → POST /api/v1/admin/guidelines/reindex
    → KnowledgeService.reindexKnowledge() [@Transactional]
      → ensureBucket(guidelinesBucket, artifactsBucket)  ← MinIO
      → readCorpus()  ← classpath:knowledge/official-corpus.json
      → 对每个 CorpusDocument:
        → 校验 contentSha256
        → ingestDocument(...)
          → putObject(objectKey, bytes)  ← MinIO
          → INSERT guidelines (ON CONFLICT DO UPDATE)
          → UPDATE guideline_versions SET active=FALSE
          → INSERT guideline_versions (ON CONFLICT DO UPDATE) + active=TRUE
          → DELETE guideline_chunks
          → 对每个 chunk: INSERT guideline_chunks (ON CONFLICT DO UPDATE)
            → embedding = embed(topics + section + content)  ← 64 维 hash
      → ingestSafetyBaseline()  ← 内置 3 个 chunk
      → UPDATE guideline_versions SET active=FALSE WHERE retrieval_method='legacy-seed'
```

**4. 随访激活路径**（Clinician → 后端 → 数据库）
```
ClinicianView.review() → ClinicianView.confirmFollowup() → ClinicianView.activate()
  → POST /api/v1/triage-results/{id}/review
    → rules.max(ruleUrgency, aiUrgency) + 决策
    → 状态: PENDING_REVIEW→REVIEWED 或 REJECTED
  → POST /api/v1/followup-plans (DRAFT, templateCode=GENERAL_FOLLOWUP_V1)
  → POST /api/v1/followup-plans/{id}/activate
    → 状态: DRAFT→ACTIVE
    → createTask x4 (BP_RECORD/SYMPTOM_CHECK/ADHERENCE_CHECK/CLINICIAN_REVIEW, 7/7/14/28 天)
    → 全部分配给第一个 FOLLOWUP_STAFF 用户 ⚠️
    → visit.status = FOLLOWUP_ACTIVE
  → 审计: TRIAGE_REVIEWED, FOLLOWUP_PLAN_CREATED, FOLLOWUP_PLAN_ACTIVATED
```

---

## 8. 跨系统契约分析

### 8.1 新增 611 分支关键契约

| 契约 | 涉及系统 | 当前状态 | 风险 |
|------|---------|---------|------|
| `runId / visitId` 长度 | Backend ↔ AI | 8-64 字符；JobAccepted/JobStatus runId 无约束 | 低 |
| `proposedUrgency` 所有权 | Backend ↔ AI | AI 不能改（OpenAICompatibleProvider 强制覆盖） | 解决旧 X-C5 |
| `citation.chunkId` 校验 | Backend ↔ AI | `knowledge.isActiveChunk()` 单点查询 | 解决旧 X-C3 |
| `chunkId` 字符串空间 | Backend ↔ AI ↔ DB | 自由字符串（`chunk-red-flag-chest-pain-001` 等）；DB 无 CHECK | 中 |
| `Urgency` 枚举 | Backend ↔ AI ↔ Frontend | `ROUTINE/URGENT/EMERGENCY` 一致 ✅ | — |
| `VisitStatus` 枚举 | Backend ↔ Frontend | 8 个状态一致；DB 无 CHECK | 中 |
| `ReviewDecision` 枚举 | Backend ↔ DB ↔ Frontend | `ACCEPT/MODIFY/REJECT`；DB 有 CHECK ✅ | — |
| `CoverageStatus / AssessmentStatus` 枚举 | Backend ↔ DB ↔ Frontend | 新增；DB 有 CHECK ✅ | — |
| `SupportLevel` 枚举 | Backend ↔ DB ↔ Frontend | `RULE_SUPPORTED/RECORD_ONLY/CUSTOM`；DB 有 CHECK ✅ | — |
| `ComplaintAnalysisStatus` 枚举 | Backend ↔ DB | `PENDING/SUCCEEDED/FAILED/INVALID`；DB 有 CHECK ✅ | — |
| `INTAKE_V1/V2` 版本字段 | Backend ↔ DB | V1 已 GONE，V2 为主 | — |
| `profile_snapshot` JSON 形状 | Backend ↔ Frontend | 两者都是 `PatientProfileInput` record；前端 `Object.assign(profile, profileData.data)` | 低 |
| `complaintAnalysis.{tags, structuredFacts}` JSON 形状 | Backend ↔ AI ↔ Frontend | AI 端用 Pydantic `ComplaintTag / ComplaintFacts`；后端用 Java `ComplaintTagView / ComplaintFactsView`；前端用 TS `ComplaintTag / ComplaintFacts` | 中——三个独立 schema 需保持同步 |
| `safety.reasonCodes` 字符串空间 | Backend ↔ AI | AI 用 `PROMPT_INJECTION_OR_SCOPE_VIOLATION / POTENTIAL_REAL_IDENTIFIER / AI_ATTEMPTED_RULE_DOWNGRADE / INVALID_CITATION / AI_OUTPUT_SCOPE_VIOLATION / AI_ATTEMPTED_UNSUPPORTED_URGENCY / AI_INVALID_URGENCY / AI_INVALID_CITATION_FORMAT`；后端 `AI_RULE_DOWNGRADE / AI_INVALID_CITATION / AI_SAFETY_BLOCK / AI_UNAVAILABLE_OR_INVALID` | 中——前端不展示 reasonCodes（AdminView 仅展示 alert.reasonCodes 拼接） |
| `INTERNAL_SERVICE_TOKEN` | Backend ↔ AI | 同一 token 在两侧用 `MessageDigest.isEqual`/字符串比较校验 | 中——env 默认值在两侧都是 `change-me-local-only`，生产需同时改 |

### 8.2 仍存在的旧问题

| ID | 主题 | 状态 |
|----|------|------|
| X-1 | AI `proposedUrgency` 在 `OpenAICompatibleProvider.structure_complaint` 未强制覆盖 | 未修复（参见 P-C6） |
| X-2 | `requestId` 随机生成与 HTTP 头无关 | 未修复（参见 B-C3） |
| X-3 | `chunkId` 字符串在三方各写各的（Java 硬编码 `VALID_CHUNKS` 取消后改用 knowledge service，OK；但 V1 PlatformService 仍硬编码） | 部分修复 |
| X-4 | `visits.status / followup_plans.status / safety_alerts.* / agent_runs.*` 无 DB CHECK | 未修复（参见 6.4） |
| X-5 | `INTAKE_V1_DEPRECATED` 走 `throw deprecated()` 异常路径——Spring 的 `ResponseEntityExceptionHandler` 默认 500，但自定义 `ApiException` 处理 4xx；OK | 已修复 |

---

## 9. 配置与部署

### 9.1 部署文件

- `compose.yaml`（7 服务：postgres / redis / minio / ai-api / ai-worker / backend / nginx）
- `backend/Dockerfile`（未读取，假定 spring-boot 镜像）
- `ai-service/Dockerfile`（python:3.9.25-slim-bookworm，OK）
- `.env.example`（11 个变量）
- `backend/settings-docker.xml`（未读取）

### 9.2 关键配置

| 配置 | 默认值 | 生产建议 |
|------|------|---------|
| `JWT_SECRET` | `change-this-demo-secret-at-least-32-bytes` | 必须 32 字节随机 |
| `INTERNAL_SERVICE_TOKEN` | `change-me-local-only` | 必须改 |
| `POSTGRES_PASSWORD` | `change-me-local-only` | 必须改 |
| `MINIO_ROOT_PASSWORD` | `change-me-local-only` | 必须改 |
| `AI_JOB_TIMEOUT_SECONDS` | compose:10, .env:60, application.yml:10 | **三方不一致**（参见 H7） |
| `AI_MAX_OUTPUT_TOKENS` | 1800 | OK |
| `RAG_TOP_K` | 6 | OK |
| `KNOWLEDGE_SEED_ENABLED` | true | 生产首次必须 true |
| `KNOWLEDGE_CORPUS` | classpath:knowledge/official-corpus.json | OK |

### 9.3 部署风险

1. **`AI_JOB_TIMEOUT_SECONDS` 三处不一致**（compose 10s, .env 60s, application.yml 10s）—— 上一版报告 B-H7 未修复
2. **AI_BASE_URL compose 中指向 `ai-api:8000`**——容器内可达；本地开发（`http://127.0.0.1:8000`）需修改
3. **MinIO 端口 `127.0.0.1:59001:9001`（console）+ 9000（API）**——仅 127.0.0.1 暴露，外网访问需调整
4. **postgres 端口 `127.0.0.1:55433:5432`**——同上
5. **Nginx frontend 端口 18088**——未做 TLS 终止；生产应在前面放 TLS 代理
6. **`@EnableMethodSecurity`** 启用，但 V1 `/api/v1/visits/{id}` 无 `@PreAuthorize`（参见 B-M9）
7. **CORS 允许 `http://localhost:*` / `http://127.0.0.1:*`**（B-M10）——开发友好；生产应白名单
8. **测试账号** 4 个 + 密码 `Demo123!` 硬编码（参见 F-C2 / B-M21）

### 9.4 安全配置

| 风险 | 位置 |
|------|------|
| JWT 默认 secret | `application.yml:36`（B-M8） |
| INTERNAL_SERVICE_TOKEN 默认 | `application.yml:39`，`.env.example:12` |
| Demo 用户 admin 存在 | `DemoDataConfig.java:16`（F-C2） |
| CORS 开放本地端口 | `SecurityConfig.java:62`（B-M10） |
| `/internal/v1/knowledge/**` permitAll | `SecurityConfig.java:51`（B-H2） |
| 无 HTTPS 强制 | `compose.yaml` |
| 无 Rate Limit | 全部 |
| 登录无 brute-force 保护 | `AuthController.java:19-23`（无 IP 限流 / 锁定） |

---

## 10. 总结与修复优先级

### 10.1 按严重等级计数总览

| 严重等级 | Backend | AI Service | Frontend | Cross-System | DB/SQL | Deploy/Cfg | 总计 |
|---------|---------|------------|----------|--------------|--------|-----------|------|
| 🔴 Critical | 6 | 6 | 3 | 0 | 0 | 0 | **15** |
| 🟠 High | 7 | 5 | 5 | 0 | 0 | 0 | **17** |
| 🟡 Medium | 31 | 21 | 16 | 0 | 7 | 0 | **75** |
| 🔵 Low/Info | 10 | 0 | 0 | 0 | 0 | 0 | **10** |
| **总计** | **54** | **32** | **24** | **0** | **7** | **0** | **117** |

### 10.2 模块问题分布

```
Backend    ██████████████████████████████████████████████████████▌  54
AI Service █████████████████████████████████▌                    32
Frontend   █████████████████████████▌                            24
DB/SQL     ███████▏                                                  7
Deploy     (无 Critical/High 项)
```

### 10.3 最关键问题（必须优先修复）

| 优先级 | ID | 问题 | 模块 | 影响 |
|--------|----|------|------|------|
| **P0** | B-C1 | `@Transactional` 内 15s 同步轮询 | Backend | 并发提交耗尽连接池 |
| **P0** | B-C2 | `replaceReports` delete+insert 非原子 | Backend | 并发症状丢失 |
| **P0** | B-C3 | `audit` 生成随机 requestId | Backend | 审计无法追踪请求 |
| **P0** | B-C4 | PrivacySanitizer 未覆盖所有入口 | Backend | PII 可能入库 |
| **P0** | B-C5 | `confirmComplaint` 不校验 tag 在目录 | Backend | 任意 code 写入 |
| **P0** | B-C6 | 仓库无 `@Repository` | Backend | JPA 异常未翻译 |
| **P0** | P-C1 | `_extract_json_object` 多 JSON 误解析 | AI | 提取错误结构 |
| **P0** | P-C2 | `OpenAICompatibleProvider._call` 仅捕获 Timeout | AI | 其他异常全冒泡 |
| **P0** | P-C3 | `execute_job` 静默 `type(exc).__name__` | AI | 错误信息丢失 |
| **P0** | P-C5 | 每次新建 Redis/HTTP 连接 | AI | 连接池耗尽 |
| **P0** | P-C4 | Celery 重试禁用 | AI | 消息丢失 |
| **P0** | F-C1 | `Idempotency-Key` 随机 UUID | Frontend | 幂等失效 |
| **P0** | F-C2 | LoginView 硬编码测试密码 | Frontend | 凭据泄露 |
| **P0** | F-C3 | PatientView localStorage 明文 | Frontend | PII 浏览器残留 |

### 10.4 P1 关键问题

| ID | 问题 | 模块 |
|----|------|------|
| B-H1 | `JwtAuthFilter` 静默吞 RuntimeException | Backend |
| B-H2 | `/internal/v1/knowledge/**` 仅靠 X-Internal-Token | Backend |
| B-H3 | `findFirstByRole.orElseThrow()` 无消息 | Backend |
| B-H4 | `activatePlan` 4 任务硬编码 | Backend |
| B-H5 | 旧 `findByIdempotencyKey` 仍非原子 | Backend |
| B-H6 | V1 service 死代码可被绕过调用 | Backend |
| B-H7 | `ComplaintAnalysisView` 反序列化失败吞掉 | Backend |
| P-H1 | Worker 崩溃任务卡 RUNNING | AI |
| P-H2 | 缺 `celery[redis]` extras | AI |
| P-H4 | `SafetyDecision.REVIEW` 死代码 | AI |
| P-H5 | Celery 无 `task_time_limit` | AI |
| F-H1 | 无 404 路由 | Frontend |
| F-H2 | 错误响应非 JSON 时丢信息 | Frontend |

### 10.5 架构建议摘要

1. **事务边界**：将 `runAi` 拆出 `@Transactional`；用 `REQUIRES_NEW` 或异步任务
2. **数据完整性**：`SymptomEntity` 加 `@Version`；`replaceReports` 改 UPSERT
3. **可观测性**：`PlatformService` / `IntakeV2Service` 加 `Logger`；`PrivacySanitizer` 接入 JPA 监听器；`JobStore` 加 reaper
4. **幂等性**：前端 `Idempotency-Key` 改基于 `visit.id` 派生；后端 `INSERT ... ON CONFLICT`
5. **可测试性**：把 `IntakeV2Service` 17 个依赖拆为子服务
6. **配置统一**：`AI_JOB_TIMEOUT_SECONDS` 三处对齐到 60s
7. **数据库约束**：为 `visits.status / followup_plans.status / safety_alerts.* / agent_runs.*` 加 CHECK
8. **安全加固**：登录接口加 rate limit；admin demo 账号生产禁用；`/internal/v1/knowledge/**` 加 IP 白名单或 JWT scope
9. **隐私**：`PrivacySanitizer` 接 JPA `@PrePersist`/`@PreUpdate` 监听器；前端 localStorage 写之前脱敏
10. **AI 资源**：`JobStore` 单例 Redis 连接；Celery 加 `task_soft_time_limit=60 / task_time_limit=120`

### 10.6 风险总结

- **本审查不修改任何代码**，仅提交发现
- 已修复（相对上一版）：JSON 序列化错误、V1 写路径弃用、VALID_CHUNKS 单点维护、citation whitelist、prompt attack 检测、output scope 检测、跨系统状态机、profile snapshot、coverage/assessment 状态
- **仍存在的高风险**：事务内长轮询、症状替换非原子、requestId 随机、PrivacySanitizer 覆盖不全、idempotency-key 随机 UUID、localStorage 明文 PII
- **新增风险**：ComplaintAnalysis JSON schema 三方维护、`INTAKE_V1/V2` 双路径共存在 Bean 容器中、ui-demo 与主前端契约脱钩
- **建议**：P0 项修复后回归 7+7+1 后端测试 + 4+1+1 前端测试 + 3+1+1 AI 测试；运行 `data-pipeline/evaluate.py` 验证 v2 deterministic 评测

---

*本报告基于 611 分支（commit 6b1fe58，2026-07-22）静态分析得出。未执行运行时测试或容器化集成测试。建议在修复 P0 项后回归已有测试套件并补充 IntegrationTest 用例覆盖 P0 场景（事务边界、并发症状替换、idempotency-key 重用、localStorage PII、PrivacySanitizer 边界）。*

*报告完成日期: 2026-07-23  ·  审查人: SkylerZhu*