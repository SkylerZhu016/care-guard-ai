# Care-Guard-AI Branch `611` 全面代码审查报告

> **审查日期**: 2026-07-23  
> **分支**: `611` — `https://github.com/SkylerZhu016/care-guard-ai/tree/611`  
> **审查范围**: Full-stack: AI Service (Python/FastAPI) + Backend (Java/Spring Boot) + Frontend (Vue 3/TypeScript) + 数据管道 + 部署

## 1. 项目总体结构与架构

这是一个面向基层医疗的**预问诊与随访信息服务平台**，采用微服务架构，包含四个主要模块：

| 模块 | 技术栈 | 职责 |
|------|--------|------|
| ai-service/ | Python FastAPI + Celery + LangGraph | 异步 AI 分析工作流、护栏杆、RAG 知识检索 |
| backend/ | Java 21 + Spring Boot 3 + PostgreSQL (pgvector) | REST API、规则引擎、JWT 认证、知识索引 |
| frontend/ | Vue 3 + TypeScript + Element Plus + Pinia | 用户端 SPA（患者、医护、随访、管理） |
| data-pipeline/ | Python + requests + BeautifulSoup | 官方医疗来源的可审计语料抓取器 |

**总体评分**: 8.5/10 — 架构清晰，关注点分离合理，安全意识强

## 2. AI Service (Python)

### 2.1 工作流架构

LangGraph 5-Agent 流水线: InputGuardAgent -> EvidenceRetrieverAgent -> ClinicalSummaryAgent -> SafetyCriticAgent -> CitationVerifierAgent

### 2.2 优势

- **LangGraph 工作流设计优秀**: 5 个 Agent 形成流水线，每个职责单一，易于测试和扩展
- **Fake Provider 设计巧妙**: 在无真实 AI 服务时可独立运行，输出确定性与真实 Provider 格式一致
- **护栏杆（guardrails）完善**: 输入检测 prompt injection/PII，输出检测越界（诊断/剂量），引文验证防幻觉
- **Complaint Structure 白名单过滤**: AI 提取的标签必须存在于 availableTags 白名单中

### 2.3 关键代码问题

**P1 - providers.py JSON 提取的贪婪匹配**

```python
candidates.extend(re.findall(r"\{[\s\S]*\}", content))
```

`[\s\S]*` 是贪婪匹配，如果 content 中存在多个 {} 块，会一次匹配从第一个 { 到最后一个 } 的整个字符串。应考虑使用非贪婪 `[\s\S]*?`。

**P2 - Prompt 模板嵌入在代码中**

providers.py 中约 1/3 的代码是纯文本 prompt 模板（中英文混合），包括 few-shot 示例。建议将 prompt 模板抽取到独立的 .prompt 或 .yaml 文件中。

**P2 - workflow.py 依赖注入不彻底**

```python
def get_provider():
    return DeterministicFakeProvider() if settings.provider == "fake" else OpenAICompatibleProvider()
```

## 3. Backend (Java Spring Boot)

### 3.1 优势

- **大规模使用 Java 14+ Records**: ApiModels.java、IntakeV2Models.java 中大量使用 record
- **统一的错误处理**: @RestControllerAdvice 统一格式
- **幂等性设计**: 提交使用 Idempotency-Key 头
- **隐私脱敏贯穿服务层**: 每个写入路径都调用了 privacy.sanitize()
- **乐观锁**: @Version 防止并发冲突

### 3.2 关键代码问题

**P0 - IntakeV2Service.java 事务内 HTTP 调用**

```java
@Transactional
VisitViewV2 submit(...) {
    ...
    runAi(visit, ...);  // 同步 HTTP 轮询 AI 服务，在事务内！
    ...
}
```

在事务内调用 AI 服务（同步 HTTP 轮询），如果 AI 服务超时会长期持有数据库连接。建议将 AI 调用移到事务外。

**P0 - DemoDataConfig.java 无 @Profile("demo") 限制**

所有演示账户密码相同（Demo123!），且无 profile 限制。生产环境不应加载此配置。

**P1 - IntakeV2Service.java 过大 (~31KB)**

混合了症状目录查询、患者资料 CRUD、问诊 CRUD、投诉结构分析、标签确认、补充信息、队列管理等职责。建议拆分为多个小服务类。

**P2 - 硬编码症状目录 (IntakeCatalog.java)**

约 30 个症状及引导问题全硬编码，每次修改目录都需要重新编译部署。建议移到数据库或 YAML 文件中。

**P2 - v1 与 v2 服务层代码重复**

PlatformService 和 IntakeV2Service 的 replaceSymptoms/replaceReports 逻辑高度相似。v1 API 已标记 deprecated，建议彻底移除。

## 4. Frontend (Vue 3 / TypeScript)

### 4.1 优势

- TypeScript 类型完备，所有接口完整类型定义
- 状态管理简洁（Pinia + Composition API）
- UI/UX 考虑周到：无障碍属性丰富，患者语言优先
- 安全提示贯穿 UI：全局安全提示条 + 隐私声明

### 4.2 关键代码问题

**P1 - api.ts 中缺少请求超时**

```typescript
const response = await fetch(`/api/${version}${path}`, { ...init, headers })
```

fetch 默认没有超时。建议使用 AbortController 设置超时（如 30s）。

**P1 - PatientView.vue 体积过大 (~28KB)**

包含四个功能区域（问诊、记录、随访、健康资料）的所有逻辑，建议拆分为独立视图组件。

**P2 - 路由守卫缺少细粒度权限**

路由守卫仅检查角色身份，未限制同角色下的不同权限级别。

**P2 - ElMessageBox.confirm 的取消捕获依赖实现细节**

```typescript
if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message)
```

依赖 Element Plus 内部字符串值，版本升级时可能变化。

**P2 - 健康数据草稿存于 localStorage**

草稿存 localStorage 而非 sessionStorage，且未加密。

## 5. UI Demo

ui-demo/ 独立运行，mock.ts 实现完整 mock 数据层。但与生产前端共享组件（AppModal.vue、StatusPill.vue）存在代码重复。

**建议**: 抽取共享组件到 monorepo 的共享包中。

## 6. Data Pipeline

crawl_official_medical_sources.py 设计精良：
- 白名单来源（仅 WHO、CDC、NIH、NHS）
- robots.txt 尊重 + 域名白名单检查
- 限速 + 完整审计链（corpus.json 带 contentSha256 + crawl-report.json）

**评分**: 9.5/10

**小问题**: 文档字符串包含本地绝对路径 D:\\Anaconda\\envs\\ML3.9\\python.exe，应清理后提交。

## 7. 部署与基础设施

**优势**: compose.yaml 健康检查完善、端口绑定 127.0.0.1、数据 volume 持久化

**问题**: ai-worker 健康检查依赖 Celery inspect ping，首次启动可能失败。建议简化为 HTTP 健康端点。

## 8. 测试覆盖率

| 层 | 覆盖 | 质量 |
|--------|--------|------|
| AI Service | 13 用例 | 高质量 |
| Backend | ~10 用例 | 高质量 |
| Frontend | ~12 用例 | 中质量 |
| UI Demo | 无 | N/A |

**亮点**: test_provider_adapter.py 覆盖超时/JSON 提取/白名单过滤；PlatformIntegrationTest.java 使用 @MockBean + TRUNCATE 清理

**不足**: 前端缺乏 ClinicianView.vue、AdminView.vue 测试；PatientView.test.ts 依赖 CSS class 名称（脆弱测试）

## 9. 安全审查

| 项目 | 状态 |
|------|------|
| JWT + BCrypt + CORS 本地限制 | 通过 |
| PII 脱敏（手机/身份证/邮箱/姓名/地址） | 通过 |
| SQL 注入防护（参数化查询） | 通过 |
| 审计日志全量覆盖 | 通过 |
| 幂等性防重复提交 | 通过 |
| Prompt Injection 检测 | 通过 |
| AI 输出越界检测 | 通过 |
| 引用溯源防幻觉 | 通过 |
| Urgency 降级/无中生有防御 | 通过 |

## 10. 综合评价

| 维度 | 评分 |
|------|------|
| 架构设计 | 9/10 |
| 代码质量 | 8/10 |
| 安全实践 | 9/10 |
| 测试质量 | 7/10 |
| 可维护性 | 7/10 |
| 文档 | 8/10 |
| **综合** | **8.3/10** |

**改进优先级**:
- **P0**: IntakeV2Service 事务内 HTTP 调用 / DemoDataConfig 无 @Profile 限制
- **P1**: PatientView.vue 和 IntakeV2Service 过大 / 前端 api.ts 无请求超时
- **P2**: Prompt 模板嵌入代码 / 硬编码症状目录 / 前端测试不足

**总结**: 这是一个架构精良、安全成熟的医疗 AI 全栈项目。团队在医疗级安全防护（Guardrails、隐私脱敏、审计日志）上投入了大量精力。主要改进方向是：拆分过大的服务类、将硬编码配置抽取为外部可配置项、增加前端 admin/clinician 视图的测试、以及修复事务内 HTTP 调用的潜在问题。

---

*审查完成于 2026-07-23*
