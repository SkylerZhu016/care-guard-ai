# 项目完成度全面核验报告

> 项目：基层医疗安全型预问诊与随访平台  
> 核验日期：2026-07-22  
> 核验方式：源码与配置静态检查、依赖与生产构建、真实 PostgreSQL 集成测试、API 冒烟、性能冒烟、四角色真实浏览器流程、数据库现场查询、Git/远端协作记录检查  
> 核验原则：不以 README、任务清单或注释作为完成依据；只有代码、运行结果和可重复证据共同支持时才判定为已实现。

## 1. 总体结论

**结论：部分完成。**

项目已经形成一个可以启动和演示的教学模拟闭环：四类账号可登录，患者可提交固定形式的合成病例，规则引擎可完成红旗筛查，默认 Fake Provider 可生成结构化 AI 结果及引用，医务人员可接受审核并激活计划，随访人员可完成任务，管理员可查看运行、告警和审计。后端、AI、前端测试与生产构建均能通过，Compose 长期服务实际保持 healthy。

但如果以原始任务书和仓库自身 SRS/开发指导书为验收基线，项目仍存在数项影响正式验收的实质性缺口：真正的指南入库、向量检索和 RAG 未实现；所谓多 Agent 实际是一次模型调用的线性节点链；默认且唯一被验证的是 Fake Provider；隐私脱敏未实现；审核决定、计划模板和随访任务状态机存在可复现的业务错误；若干任务书/SRS 功能只有缩减界面或没有管理入口；核心自动化测试覆盖不足；GitHub Issue/PR/评审证据为空。

因此：

- **不适合直接作为“全部需求已完成”的版本正式提交验收。**
- **可用于受限演示**，前提是明确标注“FAKE / 教学模拟”，不宣称真实 RAG、真实多 Agent 或真实模型效果，并在演示前清理本次核验产生的合成数据。
- 本次核验**未修改任何业务源码、配置或数据库结构**；仅新增本报告。API 与浏览器核验按正常业务接口写入了合成测试记录。

## 2. 需求基线与判定边界

### 2.1 明确需求基线

本报告综合以下材料形成验收清单：

1. 原始任务书 `期末任务.docx` 中的选题二要求；
2. `docs/requirements/SRS.md:3-32` 的角色、核心功能和非功能需求；
3. `docs/PROJECT_DEVELOPMENT_GUIDE.md` 对 RAG/Agent、数据模型、管理端、状态机、安全、测试、部署和协作证据的细化；
4. 当前前端页面、公开 API 和数据库结构所表达的实际产品边界。

### 2.2 属于推测或项目自定的判定

- 原始任务书的部分描述不是逐接口验收标准，因此“分步保存”“指南版本管理”“计划关闭/归档”“管理员用户/规则/模型管理”等细项主要依据仓库自己的 SRS 和开发指导书判断。
- 高血压是仓库自选的 MVP 随访模板；未要求扩展糖尿病等更多病种。
- Fake Provider 可用于工程演示，但仓库指导书明确说明最终答辩应使用真实配置，且不得把 Fake 冒充真实模型；因此本报告把“Fake 闭环可用”和“真实 AI 已完成”分开判断。
- LibreOffice 不是当前应用运行依赖。原始 DOCX 的核验不依赖项目内的 LibreOffice 集成；路径风险依据实际 README/脚本中的 Windows、WSL 路径写法判断。

## 3. 已完成的功能及证据

| 能力 | 核验结论 | 主要证据 |
|---|---|---|
| 四角色登录与后端 RBAC | 已实现基本能力 | `backend/src/main/java/com/example/medsim/SecurityConfig.java`；四角色浏览器登录均成功；越权路径由后端注解和所有权检查限制 |
| 患者草稿、编辑、提交 API | API 已实现 | `PlatformController.java:17-19`；`PlatformService.java:27-66`；提交要求症状并执行幂等键查询 |
| 确定性红旗规则 | 已实现 | `backend/src/main/java/com/example/medsim/RuleEngine.java`；后端规则测试通过；胸痛+呼吸困难实测进入 `EMERGENCY` |
| AI 异步调用链 | Fake 模式可运行 | `ai-service/app/jobs.py`、`celery_app.py`；Redis、Celery worker 均 healthy；API 冒烟得到 `SUCCEEDED` 和非空引用 |
| AI 紧急度不可降低和引用白名单 | 已实现基本防线 | `ai-service/app/workflow.py:31-60`；`PlatformService.java:142-158`；相关 Pytest 通过 |
| 人工审核 API | 接受路径可运行 | `PlatformController.java:23`；`PlatformService.java:80-90`；浏览器接受审核流程成功 |
| 随访计划激活 | 单一固定模板路径可运行 | `PlatformService.java:93-123`；激活实测创建 4 个任务 |
| 随访任务更新 | 正常完成请求可运行 | `PlatformService.java:127-134`；浏览器和 API 均可提交完成记录 |
| 告警、审计、运行记录 | 已有基础读取界面和数据 | `PlatformService.java:136-138`；`frontend/src/views/AdminView.vue`；管理员页面实测可查看 |
| 数据库迁移 | 可在真实 PostgreSQL 执行 | `backend/src/main/resources/db/migration/V1__baseline.sql`；Spring 测试使用 PostgreSQL + Flyway 成功 |
| 容器化部署 | 长期服务可运行 | `compose.yaml`；postgres、redis、minio、ai-api、ai-worker、backend、nginx 实测均 healthy；`minio-init` 正常退出 0 |
| 前端生产产物 | 可构建 | `frontend/Dockerfile`；网络恢复后执行 `docker compose build --no-cache nginx` 成功，2015 个模块完成转换并导出 `medsim-nginx:latest` |
| 基础响应式界面 | 可用 | 真实浏览器核验四类工作台；633px 视口未见页面级横向溢出；控制台未见错误 |
| 基础性能 | 当前样本达标 | 50 个请求、并发 10、失败 0、P95 75 ms，低于 SRS 的 500 ms 目标 |

静态搜索未发现业务源码中的明显 `TODO`、`FIXME`、字面空函数或 `NotImplemented`。Python 中的 `pass` 是异常类型空类或评测脚本吞异常，并非可直接据此认定的占位函数。当前主要风险是“有可运行的简化替身实现”，而非完全空白代码。

## 4. 未完成、不可用或存在疑问的功能

### 4.1 AI、RAG 与多 Agent

1. `ai-service/app/knowledge.py:4-41` 只有 3 条代码内硬编码知识片段，检索是关键词计分和排序，不是向量检索。
2. 数据库迁移中没有指南、知识分块、Embedding 或向量表；现场查询 `pg_extension` 只有 `plpgsql`，没有启用 `vector`。虽然 `compose.yaml:4` 使用 pgvector 镜像，但实际未使用 pgvector 能力。
3. `ai-service/app/workflow.py:64-75` 是 `InputGuard → GuidelineRetriever → RiskReviewer → SafetyAndCitationVerifier` 的固定线性链，且仅 `RiskReviewer` 调用一次 provider（`workflow.py:25-28`）。这不等于多个独立 Agent 的协作、路由、反思或互审。
4. MinIO 只在 `compose.yaml:44-57` 创建 `guidelines` 和 `artifacts` 桶；应用源码和依赖未发现上传、读取、解析或入库 MinIO 的实现。
5. `ai-service/app/providers.py:39-64` 存在 OpenAI-compatible 适配器，但真实 provider、真实密钥、异常响应格式和内容安全均未做现场验证。
6. 输出阶段只检查紧急度和引用 ID；没有实现安全文档所称的诊断、药品、剂量、免责声明等输出审查。`docs/security/SECURITY_AND_COMPLIANCE.md:12` 与实际代码不一致。

**判定：Fake 工程链路已完成；真实 RAG、多 Agent、真实模型安全链未完成。**

### 4.2 隐私脱敏

- `PlatformService.clean()` 在 `backend/src/main/java/com/example/medsim/PlatformService.java:170` 只删除部分控制字符并 `strip`，不会识别或掩码电话号码、身份证号、姓名或地址。
- 含疑似电话号码的输入会被 AI InputGuard 标为 `BLOCKED`，但自由文本此前已经由后端写入数据库。现场查询发现 `visits.free_text` 中仍有 **1 行包含 11 位测试号码明文**。
- 这与原任务的“信息脱敏”和 `docs/security/SECURITY_AND_COMPLIANCE.md:5,13` 的声明不符。

**判定：阻断提示不等于数据脱敏；该功能未完成。**

### 4.3 审核、计划和任务状态机

现场构造异常输入后得到以下结果：

| 场景 | 期望 | 实际 |
|---|---|---|
| 提交病例不带 `Idempotency-Key` | 400 业务错误 | HTTP 500 |
| 审核提交 `decision="REJECT"` | 拒绝或进入 `REJECTED` | HTTP 200，病例仍进入 `REVIEWED` |
| 创建计划传未知模板代码 | 400/404 | 创建成功并原样保存未知代码 |
| 同一已审核病例重复创建计划 | 冲突/幂等 | 可创建多个计划 |
| 任务 `PENDING → COMPLETED` | 应先进入 `IN_PROGRESS` | 允许 |
| 已完成任务再次 `COMPLETED` | 冲突或幂等且不覆盖历史 | 允许 |
| 任务 `COMPLETED → IN_PROGRESS` | 禁止回退 | 允许 |

代码原因：

- 控制器的必填请求头在方法参数绑定阶段失败，而全局异常映射未正确转成 400：`PlatformController.java:19`。
- `ReviewInput` 只要求非空字符串，未用枚举约束：`ApiModels.java:14`；`PlatformService.java:80-90` 保存任意 decision 并一律转到 `REVIEWED`。
- `PlatformService.java:93-101` 不校验模板是否存在，也没有同病例唯一约束。
- `PlatformService.java:127-132` 只校验目标状态属于 `IN_PROGRESS` 或 `COMPLETED`，没有校验当前状态和合法迁移。
- 数据库 `V1__baseline.sql:77-98` 没有补足相关唯一约束或检查约束。

### 4.4 前端与管理功能缩水

- 患者页在 `frontend/src/views/PatientView.vue:5-7` 固定两种症状；创建后立即提交。界面没有自由增删症状、真正的分步保存、恢复草稿或输入完整结构化问卷。
- 医务人员页在 `frontend/src/views/ClinicianView.vue:6` 把审核决定硬编码为 `ACCEPT`，并在一次按钮操作中立即创建和激活计划；没有修改/拒绝选择，也没有计划编辑确认阶段。
- 随访页只提供“完成”动作，没有开始、取消、逾期、暂停计划或归档闭环：`frontend/src/views/FollowupView.vue:3`。
- 管理端只有运行、告警、审计视图，没有用户、指南入库/版本、规则版本、Prompt/模型配置和权限管理。与 `docs/requirements/SRS.md:12` 及开发指导书的管理员范围不一致。
- 未发现独立指南检索页面、指南版本激活、定期提醒、计划完成/暂停/取消、最终结果归档等实现。
- 合成数据累计后，随访任务已经达到 44 条，页面一次性加载和渲染，没有分页、过滤或归档。

### 4.5 Git 协作证据

- 本地当前仅看到 2 个有效提交：`1df8ba9`、`cd62dea`；`611`、`main`、`integration` 指向同一提交。
- 远端为 `https://github.com/SkylerZhu016/care-guard-ai.git`。通过 GitHub 公共 API 现场检查时为 **0 Issue、0 Pull Request**。
- 仓库虽有模板和分支命名，但没有满足开发指导书所要求的 Issue、PR、评审和协作过程证据。

## 5. 实际运行与测试结果

### 5.1 自动化测试和构建

| 项目 | 实际结果 | 说明 |
|---|---:|---|
| 后端测试 | 5/5 通过 | 真实 PostgreSQL + Flyway；JaCoCo 行覆盖约 59.05%，分支约 31.03% |
| AI 测试 | 6/6 通过 | 覆盖率 82.82%；未覆盖 Redis/Celery 成功链和真实 provider |
| 前端测试 | 3/3 通过 | 行覆盖约 44.94%，函数覆盖仅约 10%；核心工作台几乎没有组件行为测试 |
| 前端宿主生产构建 | 通过 | `vue-tsc -b && vite build` 成功 |
| Nginx 无缓存镜像构建 | 通过 | 网络恢复后 `docker compose build --no-cache nginx` 成功；下载期间有多次 `ETIMEDOUT` 重试，但最终安装 235 个包并完成构建 |
| API 冒烟 | 通过 | AI `SUCCEEDED`、引用非空、审核后计划生成 4 个任务、任务可完成 |
| 性能冒烟 | 通过 | 50 请求、并发 10、0 失败、P95 75 ms |
| 四角色浏览器流程 | 通过基本闭环 | 登录、患者提交、医务审核、随访完成、管理员查看均可执行；控制台无明显错误 |

README `README.md:70` 记录的性能 P95 为 25 ms，而本次现场运行结果为 75 ms。两者都满足 500 ms 目标，但文档基线应记录具体运行时间和环境，避免把不同批次结果混为同一次测试。

### 5.2 测试有效性评估

自动化测试“通过”不能证明全部业务可用：

- 后端唯一集成流程在 `backend/src/test/java/com/example/medsim/PlatformIntegrationTest.java:28-68` Mock 了 `AiClient`，主要验证降级/阻断路径，没有覆盖真实 Celery 链、计划激活和完整随访状态机。
- 前端只有 `router.test.ts` 和 `StatusPill.test.ts` 等少量测试；患者、医务、随访、管理员四个核心工作台没有充分行为测试。
- AI 评测集有 60 行，但忽略病例编号和轻微数值变化后只有约 8 类语义模式，容易高估覆盖面。
- `data-pipeline/evaluate.py:28` 只检查 citation ID 是否以 `chunk-` 开头，没有与数据集中的 `retrievalExpectation.relevantChunkIds` 对照；`evaluate.py:36-37` 还会吞掉所有异常，可能让具体失败原因不可见。
- 性能脚本默认阈值是 1000 ms，而 `docs/requirements/SRS.md:29` 的目标是 500 ms。本次 P95 仍实际低于 500 ms，但脚本门禁本身过宽。

### 5.3 数据库现场状态

核验结束时现场数据库计数：

```text
visits=13
plans=13
tasks=44
phone_rows=1
pg_extension=plpgsql
```

这些数据均为本项目原有或本次 API/浏览器核验写入的合成测试数据，不是真实患者数据。演示前建议使用明确、可重复的数据库重置/seed 流程清理，否则历史队列和 44 条任务会影响演示观感，也会掩盖重复计划问题。

## 6. 安装、启动与路径核验

### 6.1 可安装和启动的部分

- Compose 镜像版本大多固定，健康检查完整；当前长期服务均 healthy。
- 网络恢复后，从空前端依赖缓存路径进行 Nginx 镜像构建成功，证明锁文件和前端构建链当前可用。
- 后端测试可在 Java 21 上完成，AI 可在 Python 3.9 环境完成测试。

### 6.2 新环境可复现性问题

1. README 本地命令硬编码当前机器路径：`D:\Anaconda\envs\ML3.9\python.exe`、`H:\Java\jdk-21`、`H:\Maven\repository`、`H:/pnpm-store`，见 `README.md:20-43,60-65`。另一台机器通常不能直接复制执行。
2. `frontend/.npmrc:1` 和 `backend/pom.xml:23` 也写入 H 盘绝对路径，属于构建配置层面的可移植性风险。
3. README 的 Compose 命令固定 WSL 发行版 `HermesUbuntu` 和 `/mnt/d/工程实训/final`，见 `README.md:53`；这不是“全新环境通用命令”。
4. 按 README 原样执行 `docker compose up --build -d --wait` 时，一次性 `minio-init` 正常退出 0 后，Compose 最终进程仍可能返回退出码 1，尽管长期服务全部 healthy。相关定义为 `compose.yaml:44-57`。
5. `/v3/api-docs` 经 Nginx 返回 SPA HTML，不是 OpenAPI JSON。`deploy/nginx.conf:13-15` 只代理 `/api/` 和 `/actuator/`。仓库虽有静态 `docs/api/openapi.yaml`，但运行态接口文档入口不通。

### 6.3 LibreOffice 与 `\`/`/` 路径注意事项

本次核验特别按用户提示区分了路径语义：

- Windows/PowerShell 使用 `D:\工程实训\final` 和反斜杠；WSL/Docker 使用 `/mnt/d/工程实训/final` 和正斜杠，未把 Windows 路径直接传给 Linux 进程。
- `docs/testing/TEST_REPORT.md:20` 也声明了这一区分，但 README 同时出现 `H:\...` 与 `H:/...` 写法，且大量绝对路径只适用于当前机器。
- 仓库没有业务代码调用 LibreOffice/`soffice`，所以当前应用不会直接触发 LibreOffice 转换路径错误。若后续用 LibreOffice 处理 `期末任务.docx` 或导出 PDF，必须根据进程所属环境先规范化路径：Windows 原生进程使用 Windows 绝对路径，WSL/Linux 进程使用 `/mnt/<drive>/...`；含中文和空格的路径必须作为单个参数传递并正确引用。不能只做字符串级 `\` 与 `/` 替换后跨环境调用。

## 7. 问题清单与严重程度

严重程度定义：**阻塞**＝直接影响任务书核心能力或正式验收真实性；**高**＝核心业务/安全错误但可绕开演示；**中**＝影响可维护性、完整性或新环境复现；**低**＝文档、体验或证据质量问题。

### 7.1 阻塞

| ID | 问题 | 影响 |
|---|---|---|
| B-01 | 真正的指南入库、Embedding、pgvector 检索和 RAG 未实现 | 不能宣称完成任务要求的 RAG/指南知识库能力 |
| B-02 | “多 Agent”实际为单次 provider 调用的线性工作流 | 不能用节点命名代替多 Agent 实现证据 |
| B-03 | 仅 Fake Provider 被完整验证，真实 provider 与真实输出安全未验证 | 无法验收真实 AI 能力和安全性 |
| B-04 | 隐私脱敏未实现，数据库可保存测试电话号码明文 | 违反任务和项目自身安全声明 |
| B-05 | 拒绝审核被当作已审核，关键业务状态含义错误 | 正式验收中的拒绝流程不可用 |

### 7.2 高

| ID | 问题 | 影响 |
|---|---|---|
| H-01 | 随访任务状态可跳跃、重复完成、完成后回退 | 审计和业务历史不可信 |
| H-02 | 未知模板和同病例重复计划均可创建 | 产生非法或重复业务数据 |
| H-03 | 患者、医务和随访界面只覆盖硬编码的最短路径 | 多项 SRS 用户故事无法从界面完成 |
| H-04 | 管理端缺用户、指南、规则、模型/Prompt 版本管理 | 管理员核心职责未完成 |
| H-05 | 后端、前端测试未覆盖上述状态机和主要工作台 | 测试绿灯无法防止核心功能不可用 |

### 7.3 中

| ID | 问题 | 影响 |
|---|---|---|
| M-01 | 缺少幂等请求头时返回 500 | 客户端错误被误报为服务故障 |
| M-02 | README、`.npmrc`、`pom.xml` 硬编码 D/H 盘和指定 WSL | 全新环境不能按文档直接运行 |
| M-03 | `docker compose ... --wait` 受一次性服务退出码影响 | 一键启动会给出误导性失败状态 |
| M-04 | Nginx 未代理运行态 OpenAPI 地址 | 演示/联调访问 `/v3/api-docs` 得到错误内容 |
| M-05 | AI 评测引用断言过弱且吞异常 | 报告可能显示高指标但未验证期望检索结果 |
| M-06 | 无 Issue/PR/评审记录，仅少量提交 | 团队协作与过程分无法提供证据 |
| M-07 | 任务列表无分页/筛选/归档 | 数据增长后页面拥挤且操作困难 |

### 7.4 低

| ID | 问题 | 影响 |
|---|---|---|
| L-01 | README 的 P95 基线与本次现场值不同 | 不影响达标，但报告可追溯性不足 |
| L-02 | 下载依赖时出现多次网络超时重试 | 当前已成功，不是代码阻塞，但离线/弱网复现不稳 |
| L-03 | 文档声明的输出审查强于实际实现 | 容易在答辩时形成错误承诺 |

## 8. 影响验收的阻塞性问题

正式验收前至少必须处理：

1. 完成可验证的公开指南来源、版本、许可、文件存储、分块、Embedding、pgvector 入库和检索，并让 citation 真正来自检索结果；
2. 明确“多 Agent”的实际验收定义，并实现至少可观察的角色分工、路由/互审或将文档和答辩口径降级为“受控单工作流”，不能虚报；
3. 接入并回归真实 provider，增加结构化输出校验、诊断/处方/剂量等越界内容审查以及故障降级验证；
4. 在写库前实现隐私标识识别、拒绝或不可逆掩码，并清理现有含号码测试记录；
5. 修复审核 decision 和随访任务状态机，补充数据库约束与负向集成测试；
6. 若评分包含团队协作过程，补充真实 Issue/PR/评审流程；历史记录不能靠事后伪造，应如实说明现状。

## 9. 建议修复顺序

1. **先修数据与业务正确性**：脱敏、审核 decision、任务状态机、模板校验、重复计划、异常 HTTP 状态；同时补数据库约束和集成测试。
2. **完成真正的知识链路**：指南来源登记 → MinIO → 解析/分块 → pgvector → 检索断言 → citation 可追溯。
3. **补真实 AI 安全闭环**：真实 provider、超时/格式错误处理、输出越界审查、模型/Prompt/知识库版本记录及对抗测试。
4. **补齐界面业务分支**：患者保存/编辑和动态症状，医务接受/修改/拒绝及计划编辑，随访合法状态操作，管理员版本与用户管理。
5. **提高测试可信度**：不 Mock 的端到端 AI/Celery 集成、状态机负例、前端核心交互、数据库约束、500 ms 性能门禁、评测期望 chunk 精确比对。
6. **修复部署与路径可移植性**：去掉机器专属绝对路径，提供 Windows 和 Linux/WSL 分开的相对命令；处理 `minio-init` 一次性服务；代理或明确暴露 OpenAPI；补全 LibreOffice/文档转换时的跨平台路径约定（如确需该工具）。
7. **最后清理演示环境与证据**：重置合成数据库，准备固定 seed，核对 README/测试报告数值，整理真实提交和协作材料。

## 10. 是否适合直接提交或演示

### 直接正式提交

**不适合。** 当前版本不能诚实地宣称已全部完成任务书所要求的 RAG、多 Agent、隐私脱敏和完整业务闭环；状态机错误也会在稍有针对性的验收测试中暴露。

### 课堂或阶段性演示

**有条件适合。** 可演示的范围应限定为：

- “确定性规则 + Fake Provider + 人工接受审核 + 固定高血压教学计划 + 随访任务 + 管理监控”的工程样例；
- 全程展示 `FAKE / 教学模拟`，主动说明知识片段是内置种子、不是完整 RAG；
- 不现场演示拒绝、重复计划、复杂任务状态或真实模型；
- 演示前重置数据库并使用固定合成 seed，确保队列简洁；
- 不把当前结果描述为医疗诊断、处方或真实临床有效性。

在完成第 8 节阻塞项并重新执行全量回归之前，项目的合理标签应是：**“可运行的教学原型 / 部分完成”，而不是“已全部完成并可正式验收”。**
